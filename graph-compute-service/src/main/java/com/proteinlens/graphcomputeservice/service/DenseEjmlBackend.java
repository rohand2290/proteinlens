package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.model.EigenPairs;
import org.ejml.data.Complex_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.data.DMatrixSparseCSC;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.EigenDecomposition_F64;
import org.ejml.ops.DConvertMatrixStruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Full dense symmetric eigendecomposition: O(n^3) time and O(n^2) memory, so it fails fast
 * above {@code spectral.dense-max-n} rather than running out of memory.
 */
@Component
@ConditionalOnProperty(name = "spectral.backend", havingValue = "dense", matchIfMissing = true)
public class DenseEjmlBackend implements SpectralBackend {

    @Value("${spectral.dense-max-n:3000}")
    private int maxN = 3000;

    @Override
    public EigenPairs smallest(DMatrixSparseCSC laplacian, int k) {
        int n = laplacian.getNumRows();
        if (n > maxN) {
            throw new GraphTooLargeException(n, maxN);
        }

        DMatrixRMaj dense = new DMatrixRMaj(n, n);
        DConvertMatrixStruct.convert(laplacian, dense);

        EigenDecomposition_F64<DMatrixRMaj> eig = DecompositionFactory_DDRM.eig(n, true, true);
        if (!eig.decompose(dense)) {
            throw new IllegalStateException("Eigendecomposition failed for Laplacian n=" + n);
        }

        int m = eig.getNumberOfEigenvalues();
        Integer[] order = new Integer[m];
        for (int i = 0; i < m; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> eig.getEigenvalue(i).real));

        double[] values = new double[m];
        for (int i = 0; i < m; i++) {
            Complex_F64 ev = eig.getEigenvalue(order[i]);
            values[i] = ev.real;
        }

        int cols = Math.min(k, m);
        DMatrixRMaj vectors = new DMatrixRMaj(n, cols);
        for (int j = 0; j < cols; j++) {
            DMatrixRMaj v = eig.getEigenVector(order[j]);
            for (int i = 0; i < n; i++) vectors.set(i, j, v.get(i, 0));
        }
        return new EigenPairs(values, vectors);
    }
}
