package com.nmi.platform.skillexpert.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordBookingPaymentRequest(
        @NotBlank @Size(max = 64) String paymentReference
) {}
