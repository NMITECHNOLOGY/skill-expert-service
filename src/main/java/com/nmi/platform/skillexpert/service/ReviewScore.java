/**
 * Average and response-rate math for client reviews.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class ReviewScore {

    private ReviewScore() {}

    /** One decimal, half up. Null when nobody has reviewed yet. */
    static Double average(long one, long two, long three, long four, long five) {
        long count = one + two + three + four + five;
        if (count <= 0) {
            return null;
        }
        long weighted = one + (two * 2) + (three * 3) + (four * 4) + (five * 5);
        return BigDecimal.valueOf(weighted)
                .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Share of requests the expert answered, as a percent.
     * Cancelled requests are left out. Null until they accept or decline at least one.
     */
    static Integer responseRate(long requested, long confirmed, long declined, long completed) {
        long answered = confirmed + declined + completed;
        if (answered <= 0) {
            return null;
        }
        long considered = requested + answered;
        return (int) Math.round((answered * 100.0) / considered);
    }
}
