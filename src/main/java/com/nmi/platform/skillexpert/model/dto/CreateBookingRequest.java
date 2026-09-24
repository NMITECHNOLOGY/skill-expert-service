package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        @NotBlank @Size(max = 200) String serviceTitle,
        @Size(max = 64) String price,
        @Size(max = 500) String address,
        @Size(max = 1000) String note,
        @NotNull Instant scheduledAt
) {}
