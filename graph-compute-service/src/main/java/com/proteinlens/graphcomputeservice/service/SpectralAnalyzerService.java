package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.model.EigenPairs;
import com.proteinlens.graphcomputeservice.model.GraphMatrices;
import com.proteinlens.graphcomputeservice.model.SpectralResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ejml.data.DMatrixSparseCSC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.JDKRandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Performs spectral analysis on a normalized symmetric Laplacian L_sym.
 *
 * Pipeline:
 *   1. Symmetric eigendecomposition of L_sym
 *   2. Spectral gap detection → kStar (natural cluster count)
 *   3. Spectral embedding U (n × kStar), row-normalized
 *   4. K-means++ clustering on rows of U → clusterAssignments
 *   5. Weighted PageRank on the raw adjacency → centrality
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectralAnalyzerService {

    private final SpectralBackend backend;

    private static final int MAX_KMEANS_ITER  = 300;
    private static final int    MAX_PAGERANK_ITER = 200;
    private static final double PAGERANK_TOL      = 1e-10;
    private static final double DAMPING           = 0.85;
    private static final int    KMEANS_SEED   = 42;

    @Value("${spectral.k-max:20}")
    private int kMax = 20;

    public SpectralResult analyze(GraphMatrices matrices) {
        return analyze(matrices, null);
    }

    /** @param kOverride fixed cluster count, or null to choose by bounded eigengap */
    public SpectralResult analyze(GraphMatrices matrices, Integer kOverride) {
        DMatrixSparseCSC laplacian = matrices.laplacian();
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

        // --- Steps 1-3: smallest eigenpairs from the configured backend ---
        // kStar is unknown until the eigenvalues are seen, so request enough vectors to cover kMax / the override.
        // The bounded eigengap search starts at the number of connected components, so cover that too.
        int components = connectedComponents(matrices.adjacency());
        int wanted = Math.min(n, Math.max(Math.max(kMax, components), kOverride != null ? kOverride : 0));
        EigenPairs pairs = backend.smallest(laplacian, wanted);
        double[] eigenvalues = pairs.values();
        int m = eigenvalues.length;
        log.debug("Eigenvalues computed: first={} last={}", eigenvalues[0], eigenvalues[m - 1]);

        // --- Step 4: bounded spectral gap → kStar, maxGap ---
        int kStar = kOverride != null ? Math.max(1, Math.min(kOverride, m)) : chooseK(eigenvalues, kMax);
        kStar = Math.min(kStar, pairs.vectors().numCols);
        double maxGap = kStar < m ? eigenvalues[kStar] - eigenvalues[kStar - 1] : 0.0;
        log.debug("kStar={} maxGap={}", kStar, maxGap);

        // --- Step 5: Build embedding matrix U (n × kStar), row-major flat array ---
        double[] U = new double[n * kStar];
        for (int k = 0; k < kStar; k++) {
            for (int i = 0; i < n; i++) {
                U[i * kStar + k] = pairs.vectors().get(i, k);
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

        // --- Step 7: weighted PageRank, max-normalized to [0, 1] ---
        int[] iters = new int[1];
        double[] rank = pageRank(matrices.adjacency(), matrices.degree(), DAMPING, PAGERANK_TOL, MAX_PAGERANK_ITER, iters);
        int convergedIn = iters[0];
        log.debug("PageRank convergedIn={}", convergedIn);

        double maxRank = 0.0;
        for (double r : rank) if (r > maxRank) maxRank = r;
        double[] centrality = new double[n];
        if (maxRank > 0.0) {
            for (int i = 0; i < n; i++) centrality[i] = rank[i] / maxRank;
        }

        return new SpectralResult(eigenvalues, maxGap, kStar, assignments, centrality, convergedIn);
    }


    // -------------------------------------------------------------------------
    // k selection and centrality (package-private for tests)
    // -------------------------------------------------------------------------

    /**
     * Largest eigengap with k restricted to [number of connected components, kMax].
     * Eigenvalues are ascending; the gap after k clusters is eig[k] - eig[k-1].
     */
    static int chooseK(double[] eig, int kMax) {
        int components = 0;
        while (components < eig.length && eig[components] < 1e-8) components++;
        int lo = Math.max(1, components), hi = Math.min(kMax, eig.length - 1);
        if (lo >= hi) return lo;
        int best = lo;
        double bestGap = -1.0;
        for (int k = lo; k <= hi; k++) {
            double gap = eig[k] - eig[k - 1];
            if (gap > bestGap) { bestGap = gap; best = k; }
        }
        return best;
    }

    /** Number of connected components of the graph of A, by union-find over the CSC structure. */
    static int connectedComponents(DMatrixSparseCSC a) {
        int n = a.numCols;
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        int comps = n;
        for (int col = 0; col < n; col++) {
            for (int p = a.col_idx[col]; p < a.col_idx[col + 1]; p++) {
                int x = find(parent, a.nz_rows[p]), y = find(parent, col);
                if (x != y) { parent[x] = y; comps--; }
            }
        }
        return comps;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }
        return x;
    }

    /** Weighted PageRank on symmetric A (CSC). Column j of A equals row j, so a column sweep is a row sweep. */
    static double[] pageRank(DMatrixSparseCSC a, double[] degree, double damping, double tol, int maxIter, int[] itersOut) {
        int n = a.numRows;
        double[] r = new double[n], next = new double[n];
        Arrays.fill(r, 1.0 / n);
        for (int iter = 1; iter <= maxIter; iter++) {
            double dangling = 0.0;
            for (int i = 0; i < n; i++) if (degree[i] == 0.0) dangling += r[i];
            Arrays.fill(next, (1.0 - damping) / n + damping * dangling / n);
            for (int col = 0; col < n; col++) {
                if (degree[col] == 0.0) continue;
                double share = damping * r[col] / degree[col];
                for (int p = a.col_idx[col]; p < a.col_idx[col + 1]; p++) {
                    next[a.nz_rows[p]] += a.nz_values[p] * share;
                }
            }
            double delta = 0.0;
            for (int i = 0; i < n; i++) delta += Math.abs(next[i] - r[i]);
            double[] t = r; r = next; next = t;
            if (delta < tol) { itersOut[0] = iter; return r; }
        }
        itersOut[0] = maxIter;
        return r;
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
}
