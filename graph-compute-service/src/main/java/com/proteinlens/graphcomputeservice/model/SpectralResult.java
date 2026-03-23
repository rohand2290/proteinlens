package com.proteinlens.graphcomputeservice.model;

/**
 * Result of spectral analysis on a normalized graph Laplacian.
 *
 * eigenvalues:        sorted ascending eigenvalues of L_sym, length n
 * maxGap:             largest gap between consecutive eigenvalues (spectral gap)
 * kStar:              natural cluster count = position of largest gap + 1
 * clusterAssignments: per-node cluster index in [0, kStar), length n
 * centrality:         per-node eigenvector centrality in [0, 1], length n
 * convergedIn:        number of power iterations until centrality converged
 */
public record SpectralResult(
        double[] eigenvalues,
        double maxGap,
        int kStar,
        int[] clusterAssignments,
        double[] centrality,
        int convergedIn
) {}
