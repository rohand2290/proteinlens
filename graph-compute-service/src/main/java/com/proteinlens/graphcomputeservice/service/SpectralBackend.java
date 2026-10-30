package com.proteinlens.graphcomputeservice.service;

import com.proteinlens.graphcomputeservice.model.EigenPairs;
import org.ejml.data.DMatrixSparseCSC;

public interface SpectralBackend {

    /**
     * Smallest k eigenpairs of L_sym, eigenvalues ascending, eigenvectors as columns of an n x k matrix.
     *
     * @throws GraphTooLargeException if the backend cannot handle this graph size
     */
    EigenPairs smallest(DMatrixSparseCSC laplacian, int k);
}
