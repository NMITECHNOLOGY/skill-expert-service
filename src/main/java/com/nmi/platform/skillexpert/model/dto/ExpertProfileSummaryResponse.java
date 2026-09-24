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
        boolean available
) {}
