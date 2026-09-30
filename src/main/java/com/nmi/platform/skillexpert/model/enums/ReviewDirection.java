/**
 * Who wrote a booking review.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.enums;

public enum ReviewDirection {
    /** The person who booked the job rates the expert. This is the public score. */
    FINDER_TO_EXPERT,
    /**
     * Kept so older rows still load. New reviews cannot use this direction.
     * Experts do not rate customers.
     */
    EXPERT_TO_FINDER
}
