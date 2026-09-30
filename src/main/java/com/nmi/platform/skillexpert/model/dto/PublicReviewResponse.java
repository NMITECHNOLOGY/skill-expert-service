/**
 * A client review shown on an expert's public page.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;

public record PublicReviewResponse(
        Long id,
        String authorName,
        int stars,
        String comment,
        String serviceTitle,
        Instant createdAt
) {}
