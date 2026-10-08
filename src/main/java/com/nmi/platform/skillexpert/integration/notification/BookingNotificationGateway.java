/**
 * Best-effort in-app notifications for booking steps.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells the other party about a booking step through notification-service (source skill-expert,
 * so it shows in the Skill Expert mini app as well as the Super App). Runs after the change has
 * committed and never throws: a missing notification must not fail a booking.
 */
@Configuration
@EnableConfigurationProperties(SkillExpertNotificationProperties.class)
public class BookingNotificationGateway {

    static final String SOURCE = "skill-expert";
    static final String TYPE = "MINI_APP_EVENT";
    static final String DEEP_LINK = "nmi://miniapp/skill-expert";

    private static final Logger log = LoggerFactory.getLogger(BookingNotificationGateway.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private final NotificationClient client;
    private final SkillExpertNotificationProperties properties;

    public BookingNotificationGateway(NotificationClient client, SkillExpertNotificationProperties properties) {
        this.client = client;
        this.properties = properties;
        if (properties.enabled() && !properties.configured()) {
            log.warn("Booking notifications are OFF: AUTH_CLIENT_SKILL_EXPERT_SERVICE_SECRET is not set for this process");
        }
    }

    /**
     * @param event booking step
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBookingStep(BookingNotificationEvent event) {
        if (!properties.configured() || event.recipient() == null || event.recipient().isBlank()) {
            return;
        }
        String key = "booking-" + event.bookingId() + "-" + event.step().name()
                + (event.version() == 0 ? "" : "-" + event.version());
        try {
            client.createInApp(key, request(event, properties.zone()));
        } catch (RuntimeException ex) {
            log.warn("In-app notification for booking {} ({}) not sent: {}",
                    event.bookingId(), event.step(), ex.getMessage());
        }
    }

    static InAppNotificationRequest request(BookingNotificationEvent event, ZoneId zone) {
        String service = blankOr(event.service(), "your booking");
        String customer = blankOr(event.customerName(), "A customer");
        String expert = blankOr(event.expertName(), "Your expert");
        String when = event.scheduledAt() == null ? "" : " on " + WHEN.format(event.scheduledAt().atZone(zone));
        String amount = formatPrice(event.price());
        String price = amount == null ? "" : " (" + amount + ")";

        String title;
        String body;
        switch (event.step()) {
            case REQUESTED -> {
                title = "New booking request";
                body = customer + " wants " + service + when + ".";
            }
            case OFFERED -> {
                title = "New offer from " + expert;
                body = expert + " sent a price for " + service + price + when + ". Review and accept it to confirm.";
            }
            case CONFIRMED -> {
                title = "Booking confirmed";
                body = expert + " confirmed " + service + when + "."
                        + (amount == null ? "" : " Pay " + amount + " to secure it.");
            }
            case OFFER_ACCEPTED -> {
                title = "Offer accepted";
                body = customer + " accepted your offer for " + service + price + when + ".";
            }
            case DECLINED -> {
                title = "Booking declined";
                body = expert + " can't take " + service + when + ". Try another expert.";
            }
            case CANCELLED -> {
                title = "Booking cancelled";
                body = customer + " cancelled " + service + when + ".";
            }
            case PAID -> {
                title = "Booking paid";
                body = customer + " paid " + (amount == null ? "" : amount + " ") + "for " + service + when + ".";
            }
            case COMPLETED -> {
                title = "Job completed";
                body = expert + " marked " + service + " as done. Rate your experience.";
            }
            default -> throw new IllegalStateException("Unexpected step " + event.step());
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("bookingId", String.valueOf(event.bookingId()));
        attributes.put("step", event.step().name());
        return new InAppNotificationRequest(event.recipient(), TYPE, title, body, attributes, DEEP_LINK, SOURCE);
    }

    /**
     * Prices are stored as typed ("2500", "LKR 25,000"). Notifications show one format, the same as
     * payment-service: "LKR 2,500.00".
     *
     * @param price price as entered; may be null
     * @return formatted amount, the trimmed text when it has no number, or null when blank
     */
    static String formatPrice(String price) {
        if (price == null || price.isBlank()) {
            return null;
        }
        Matcher number = AMOUNT.matcher(price.replace(",", ""));
        if (!number.find()) {
            return price.trim();
        }
        try {
            return String.format(Locale.ENGLISH, "LKR %,.2f", new BigDecimal(number.group(1)));
        } catch (NumberFormatException ex) {
            return price.trim();
        }
    }

    private static String blankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
