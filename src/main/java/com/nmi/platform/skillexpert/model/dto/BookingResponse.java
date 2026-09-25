package com.nmi.platform.skillexpert.model.dto;

import java.time.Instant;

import com.nmi.platform.skillexpert.model.enums.BookingStatus;

public record BookingResponse(
        Long id,
        Long expertProfileId,
        String expertName,
        String expertJobTitle,
        String customerUserId,
        String customerName,
        String serviceTitle,
        String price,
        String address,
        String note,
        Instant scheduledAt,
        BookingStatus status,
        Instant createdAt,
        String paymentReference,
        String merchantId
) {}
