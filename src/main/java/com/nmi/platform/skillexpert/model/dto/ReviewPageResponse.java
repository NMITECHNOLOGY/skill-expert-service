/**
 * One page of public client reviews plus the expert's totals.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.dto;

import java.util.List;

public record ReviewPageResponse(
        ReviewSummaryResponse summary,
        List<PublicReviewResponse> content,
        int number,
        int size,
        int totalPages,
        long totalElements,
        boolean last
) {}
