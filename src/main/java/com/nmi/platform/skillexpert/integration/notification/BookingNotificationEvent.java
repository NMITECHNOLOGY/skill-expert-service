/**
 * A booking step the other party should hear about.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import com.nmi.platform.skillexpert.model.entity.ExpertBooking;

import java.time.Instant;

/**
 * Published inside the transaction that changes the booking; handled after commit by
 * {@link BookingNotificationGateway}, so a rolled-back change sends nothing.
 *
 * @param step         what happened
 * @param bookingId    booking id
 * @param recipient    public user id of the party to tell
 * @param customerName customer's name as shown to the expert
 * @param expertName   expert's display name as shown to the customer
 * @param service      service or job title
 * @param price        agreed or offered price as entered; may be null
 * @param scheduledAt  visit time
 * @param version      distinguishes repeated steps (a revised offer); part of the idempotency key
 */
public record BookingNotificationEvent(
        Step step,
        Long bookingId,
        String recipient,
        String customerName,
        String expertName,
        String service,
        String price,
        Instant scheduledAt,
        long version
) {

    /** Booking steps, each told to the other party. */
    public enum Step {
        /** Customer asked for a booking; told to the expert. */
        REQUESTED,
        /** Expert sent a price for a custom job; told to the customer. */
        OFFERED,
        /** Expert confirmed a listed service; told to the customer. */
        CONFIRMED,
        /** Customer accepted the expert's offer; told to the expert. */
        OFFER_ACCEPTED,
        /** Expert turned the request down; told to the customer. */
        DECLINED,
        /** Customer cancelled; told to the expert. */
        CANCELLED,
        /** Customer paid; told to the expert (the customer gets the payment notification). */
        PAID,
        /** Expert finished the job; told to the customer. */
        COMPLETED
    }

    /**
     * @param step    what happened
     * @param booking the booking after the change
     * @return the event addressed to the party who did not act
     */
    public static BookingNotificationEvent of(Step step, ExpertBooking booking) {
        boolean toExpert = switch (step) {
            case REQUESTED, OFFER_ACCEPTED, CANCELLED, PAID -> true;
            case OFFERED, CONFIRMED, DECLINED, COMPLETED -> false;
        };
        String expertName = booking.getProfile() == null ? null : booking.getProfile().getDisplayName();
        String recipient = toExpert
                ? (booking.getProfile() == null ? null : booking.getProfile().getUserId())
                : booking.getCustomerUserId();
        long version = step == Step.OFFERED && booking.getProposedAt() != null
                ? booking.getProposedAt().toEpochMilli() : 0L;
        return new BookingNotificationEvent(step, booking.getId(), recipient, booking.getCustomerName(),
                expertName, booking.getServiceTitle(), booking.getPrice(), booking.getScheduledAt(), version);
    }
}
