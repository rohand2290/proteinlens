package com.proteinlens.graphcomputeservice.model;

import org.ejml.data.DMatrixRMaj;

/**
 * Smallest eigenpairs of a Laplacian.
 *
 * @param values  eigenvalues ascending; may hold more entries than {@code vectors} has columns
 *                (the dense backend returns the full spectrum, which the dashboard plots)
 * @param vectors n x k matrix whose column j is the eigenvector for values[j]
 */
public record EigenPairs(double[] values, DMatrixRMaj vectors) {}
