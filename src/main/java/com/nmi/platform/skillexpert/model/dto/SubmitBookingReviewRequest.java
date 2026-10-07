/**
 * Stars and an optional note for a finished booking.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitBookingReviewRequest(
        @NotNull(message = "Choose a star rating.")
        @Min(value = 1, message = "Choose a star rating from 1 to 5.")
        @Max(value = 5, message = "Choose a star rating from 1 to 5.")
        Integer stars,
        @Size(max = 500, message = "Keep the comment under 500 characters.")
        String comment
) {}
