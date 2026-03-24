package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.model.SpectralResult;
import lombok.extern.slf4j.Slf4j;
import org.ejml.data.Complex_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.ops.DConvertMatrixStruct;
import org.springframework.stereotype.Service;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.JDKRandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Performs spectral analysis on a normalized symmetric Laplacian L_sym.
 *
 * Pipeline:
 *   1. Symmetric eigendecomposition of L_sym
 *   2. Spectral gap detection → kStar (natural cluster count)
 *   3. Spectral embedding U (n × kStar), row-normalized
 *   4. K-means++ clustering on rows of U → clusterAssignments
 *   5. Power iteration on A_norm = I - L_sym → eigenvector centrality
 */
@Slf4j
@Service
public class SpectralAnalyzerService {

    private static final int MAX_KMEANS_ITER  = 300;
    private static final int MAX_POWER_ITER   = 1000;
    private static final double POWER_TOL     = 1e-9;
    private static final int    KMEANS_SEED   = 42;

    public SpectralResult analyze(DMatrixSparseCSC laplacian) {
        int n = laplacian.getNumRows();
        log.debug("SpectralAnalyzer: n={}", n);

        // --- n=0 guard ---
        if (n == 0) {
            log.warn("SpectralAnalyzer: empty subgraph (n=0), returning empty result");
            return new SpectralResult(new double[0], 0.0, 1, new int[0], new double[0], 0);
        }

        // --- n=1 guard ---
        if (n == 1) {
            return new SpectralResult(
                    new double[]{0.0}, 0.0, 1,
                    new int[]{0}, new double[]{1.0}, 0);
        }

        // --- Step 1: Convert sparse L to dense; pre-compute A_norm = I - L ---
        // A_norm must be computed before decompose() because decompose() may mutate dense.
        DMatrixRMaj dense = new DMatrixRMaj(n, n);
        DConvertMatrixStruct.convert(laplacian, dense);

        DMatrixRMaj aNorm = new DMatrixRMaj(n, n);
        CommonOps_DDRM.subtract(CommonOps_DDRM.identity(n), dense, aNorm);

        // --- Step 2: Symmetric eigendecomposition ---
        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, true, true);
        if (!eig.decompose(dense)) {
            throw new IllegalStateException("Eigendecomposition failed for Laplacian n=" + n);
        }

        // --- Step 3: Collect and sort (eigenvalue, eigenvector) pairs ascending ---
        int m = eig.getNumberOfEigenvalues();
        EigPair[] pairs = new EigPair[m];
        for (int i = 0; i < m; i++) {
            Complex_F64 ev = eig.getEigenvalue(i);
            pairs[i] = new EigPair(ev.real, eig.getEigenVector(i));
        }
        Arrays.sort(pairs, Comparator.comparingDouble(p -> p.value));

        double[] eigenvalues = new double[m];
        for (int i = 0; i < m; i++) {
            eigenvalues[i] = pairs[i].value;
        }
        log.debug("Eigenvalues computed: first={} last={}", eigenvalues[0], eigenvalues[m - 1]);

        // --- Step 4: Spectral gap → kStar, maxGap ---
        double maxGap = 0.0;
        int kStar = 1;
        for (int i = 0; i < m - 1; i++) {
            double gap = eigenvalues[i + 1] - eigenvalues[i];
            if (gap > maxGap) {
                maxGap = gap;
                kStar = i + 1;
            }
        }
        log.debug("kStar={} maxGap={}", kStar, maxGap);

        // --- Step 5: Build embedding matrix U (n × kStar), row-major flat array ---
        double[] U = new double[n * kStar];
        for (int k = 0; k < kStar; k++) {
            DMatrixRMaj vec = pairs[k].vector;
            for (int i = 0; i < n; i++) {
                U[i * kStar + k] = vec.get(i, 0);
            }
        }

        // Row-normalize U
        for (int i = 0; i < n; i++) {
            double norm = 0.0;
            for (int k = 0; k < kStar; k++) {
                double v = U[i * kStar + k];
                norm += v * v;
            }
            norm = Math.sqrt(norm);
            if (norm > 0.0) {
                for (int k = 0; k < kStar; k++) {
                    U[i * kStar + k] /= norm;
                }
            }
        }

        // --- Step 6: K-means++ clustering ---
        int[] assignments = kMeans(U, n, kStar);

        // --- Step 7: Power iteration centrality on A_norm ---
        double[] v     = new double[n];
        double[] vNew  = new double[n];
        double initVal = 1.0 / Math.sqrt(n);
        Arrays.fill(v, initVal);

        int convergedIn = MAX_POWER_ITER;
        double[] aNormData = aNorm.data; // row-major: aNormData[i*n + j]

        for (int iter = 0; iter < MAX_POWER_ITER; iter++) {
            // vNew = A_norm * v
            for (int i = 0; i < n; i++) {
                double sum = 0.0;
                int rowBase = i * n;
                for (int j = 0; j < n; j++) {
                    sum += aNormData[rowBase + j] * v[j];
                }
                vNew[i] = sum;
            }

            // L2-normalize vNew
            double norm = 0.0;
            for (int i = 0; i < n; i++) norm += vNew[i] * vNew[i];
            norm = Math.sqrt(norm);
            if (norm > 0.0) {
                for (int i = 0; i < n; i++) vNew[i] /= norm;
            }

            // Convergence check: ||vNew - v||_2
            double delta = 0.0;
            for (int i = 0; i < n; i++) {
                double diff = vNew[i] - v[i];
                delta += diff * diff;
            }
            delta = Math.sqrt(delta);

            // Swap buffers
            double[] tmp = v; v = vNew; vNew = tmp;

            if (delta < POWER_TOL) {
                convergedIn = iter + 1;
                break;
            }
        }
        log.debug("Power iteration convergedIn={}", convergedIn);

        // Sign convention: dominant component should be positive
        int maxAbsIdx = 0;
        double maxAbs = 0.0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(v[i]) > maxAbs) { maxAbs = Math.abs(v[i]); maxAbsIdx = i; }
        }
        if (v[maxAbsIdx] < 0.0) {
            for (int i = 0; i < n; i++) v[i] = -v[i];
        }

        // Normalize centrality to [0, 1]
        double maxCentrality = 0.0;
        for (int i = 0; i < n; i++) if (v[i] > maxCentrality) maxCentrality = v[i];

        double[] centrality = new double[n];
        if (maxCentrality > 0.0) {
            for (int i = 0; i < n; i++) centrality[i] = v[i] / maxCentrality;
        }

        return new SpectralResult(eigenvalues, maxGap, kStar, assignments, centrality, convergedIn);
    }

    // -------------------------------------------------------------------------
    // K-means++ via Apache Commons Math
    // -------------------------------------------------------------------------

    private int[] kMeans(double[] U, int n, int k) {
        List<IndexedPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            points.add(new IndexedPoint(i, Arrays.copyOfRange(U, i * k, (i + 1) * k)));
        }

        KMeansPlusPlusClusterer<IndexedPoint> clusterer = new KMeansPlusPlusClusterer<>(
                k, MAX_KMEANS_ITER, new EuclideanDistance(), new JDKRandomGenerator(KMEANS_SEED));
        List<CentroidCluster<IndexedPoint>> clusters = clusterer.cluster(points);

        int[] assignments = new int[n];
        for (int c = 0; c < clusters.size(); c++) {
            for (IndexedPoint p : clusters.get(c).getPoints()) {
                assignments[p.index()] = c;
            }
        }
        return assignments;
    }

    private record IndexedPoint(int index, double[] coords) implements Clusterable {
        public double[] getPoint() { return coords; }
    }

    // -------------------------------------------------------------------------
    // Internal value type for sorting eigenvalue/eigenvector pairs
    // -------------------------------------------------------------------------

    private static final class EigPair {
        final double value;
        final DMatrixRMaj vector;

        EigPair(double value, DMatrixRMaj vector) {
            this.value  = value;
            this.vector = vector;
        }
    }
}
