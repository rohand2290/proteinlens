package com.proteinlens.graphcomputeservice.model;

import org.ejml.data.DMatrixSparseCSC;

/**
 * Matrices derived from a protein subgraph.
 *
 * @param adjacency symmetric weighted adjacency A (untouched by normalization)
 * @param degree    weighted degree vector, d[i] = sum of row i of A
 * @param laplacian normalized symmetric Laplacian L_sym = I - D^{-1/2} A D^{-1/2}
 */
public record GraphMatrices(DMatrixSparseCSC adjacency, double[] degree, DMatrixSparseCSC laplacian) {}
