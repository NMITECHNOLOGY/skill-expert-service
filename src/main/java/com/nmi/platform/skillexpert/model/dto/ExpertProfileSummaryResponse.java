package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;

import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;

public record ExpertProfileSummaryResponse(
        Long id,
        String userId,
        String displayName,
        String jobTitle,
        String photoUri,
        ExpertProfileStatus status,
        Instant submittedAt,
        int completionPercent,
        boolean liveListing,
        ExpertProfileStatus updateStatus,
        boolean available,
        Double rating,
        int reviewCount,
        long completedJobs,
        Integer responseRate
) {
    public ExpertProfileSummaryResponse withClientReviews(ReviewSummaryResponse stats) {
        ReviewSummaryResponse value = stats == null ? ReviewSummaryResponse.empty() : stats;
        return new ExpertProfileSummaryResponse(
                id,
                userId,
                displayName,
                jobTitle,
                photoUri,
                status,
                submittedAt,
                completionPercent,
                liveListing,
                updateStatus,
                available,
                value.rating(),
                value.reviewCount(),
                value.completedJobs(),
                value.responseRate());
    }
}
