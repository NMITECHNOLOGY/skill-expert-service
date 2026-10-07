/**
 * The price and offer an expert sends for a custom job.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-29
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProposeBookingRequest(
        @NotBlank @Size(max = 64) String price,
        @NotBlank @Size(max = 1000) String note,
        @NotNull Instant scheduledAt
) {}
