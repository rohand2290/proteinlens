package com.proteinlens.graphcomputeservice.model;

/**
 * Result of spectral analysis on a normalized graph Laplacian.
 *
 * eigenvalues:        sorted ascending eigenvalues of L_sym, length n
 * maxGap:             eigengap after kStar eigenvalues
 * kStar:              cluster count: largest eigengap within [components, kMax], or the job's k override
 * clusterAssignments: per-node cluster index in [0, kStar), length n
 * centrality:         per-node weighted PageRank centrality, max-normalized to [0, 1], length n
 * convergedIn:        number of PageRank iterations until converged
 */
public record SpectralResult(
        double[] eigenvalues,
        double maxGap,
        int kStar,
        int[] clusterAssignments,
        double[] centrality,
        int convergedIn
) {}
