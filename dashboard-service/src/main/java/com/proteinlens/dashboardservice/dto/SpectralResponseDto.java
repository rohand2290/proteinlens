package com.proteinlens.dashboardservice.dto;

import java.util.List;

/**
 * Spectral analysis summary returned to the dashboard.
 *
 * spectralGapIndex = kStar - 1 (0-based index of the eigenvalue just before the largest gap).
 */
public record SpectralResponseDto(
        List<Double> eigenvalues,
        int kStar,
        double maxGap,
        int spectralGapIndex,
        int convergedIn
) {}
