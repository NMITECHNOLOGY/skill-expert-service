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
        String liveJobTitle
) {
    public record PortfolioItemResponse(Long id, String mediaUri, String caption, int sortOrder) {}
    public record ServiceItemResponse(Long id, String title, String price, String description, int sortOrder) {}
}
