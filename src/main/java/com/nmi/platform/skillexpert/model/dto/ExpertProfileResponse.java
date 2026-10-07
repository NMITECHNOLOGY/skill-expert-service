package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;
import java.util.List;

import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;

public record ExpertProfileResponse(
        Long id,
        String userId,
        String displayName,
        String jobTitle,
        String bio,
        String photoUri,
        List<String> skills,
        ExpertProfileStatus status,
        String reviewNote,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewedBy,
        int completionPercent,
        List<PortfolioItemResponse> portfolio,
        List<ServiceItemResponse> services,
        boolean liveListing,
        ExpertProfileStatus updateStatus,
        String liveDisplayName,
        String liveJobTitle,
        boolean available,
        /** Finder-to-expert average, one decimal. Null until the first client review. */
        Double rating,
        int reviewCount,
        long completedJobs,
        /** Share of requests this expert answered, 0–100. Null until they answer one. */
        Integer responseRate
) {
    public record PortfolioItemResponse(Long id, String mediaUri, String caption, int sortOrder) {}
    public record ServiceItemResponse(Long id, String title, String price, String description, int sortOrder) {}

    public ExpertProfileResponse withClientReviews(ReviewSummaryResponse stats) {
        ReviewSummaryResponse value = stats == null ? ReviewSummaryResponse.empty() : stats;
        return new ExpertProfileResponse(
                id,
                userId,
                displayName,
                jobTitle,
                bio,
                photoUri,
                skills,
                status,
                reviewNote,
                submittedAt,
                reviewedAt,
                reviewedBy,
                completionPercent,
                portfolio,
                services,
                liveListing,
                updateStatus,
                liveDisplayName,
                liveJobTitle,
                available,
                value.rating(),
                value.reviewCount(),
                value.completedJobs(),
                value.responseRate());
    }
}
