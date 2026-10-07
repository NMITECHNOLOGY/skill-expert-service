/**
 * Public client-review totals for one expert.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.dto;

public record ReviewSummaryResponse(
        Double rating,
        int reviewCount,
        long fiveStars,
        long fourStars,
        long threeStars,
        long twoStars,
        long oneStar,
        long completedJobs,
        Integer responseRate
) {
    public static ReviewSummaryResponse empty() {
        return new ReviewSummaryResponse(null, 0, 0, 0, 0, 0, 0, 0, null);
    }
}
