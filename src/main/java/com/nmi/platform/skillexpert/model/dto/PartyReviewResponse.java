/**
 * A review the caller wrote, or a review the other person wrote on their booking.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;

import com.nmi.platform.skillexpert.model.enums.ReviewDirection;

public record PartyReviewResponse(
        Long id,
        Long bookingId,
        ReviewDirection direction,
        int stars,
        String comment,
        String authorName,
        Instant createdAt
) {}
