/**
 * Author: Viraj Sachin
 * Created: 2026-10-07
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.integration.notification;

import com.nmi.platform.skillexpert.integration.notification.BookingNotificationEvent.Step;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BookingNotificationGatewayTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    /** 10:00 in Colombo (UTC+5:30). */
    private static final Instant VISIT = Instant.parse("2026-10-09T04:30:00Z");

    private final NotificationClient client = mock(NotificationClient.class);

    private BookingNotificationGateway gateway(String secret) {
        return new BookingNotificationGateway(client, new SkillExpertNotificationProperties(
                true, null, secret, "http://auth", "", null, "Asia/Colombo"));
    }

    private static BookingNotificationEvent event(Step step, long version) {
        return new BookingNotificationEvent(step, 42L, "user-1", "Dilshan", "Viraj", "Leak repair",
                "LKR 2,500", VISIT, version);
    }

    @Test
    void newRequestTellsTheExpertWhoWantsWhatAndWhen() {
        InAppNotificationRequest request = BookingNotificationGateway.request(event(Step.REQUESTED, 0), COLOMBO);

        assertThat(request.title()).isEqualTo("New booking request");
        assertThat(request.body()).isEqualTo("Dilshan wants Leak repair on Oct 9, 10:00 AM.");
        assertThat(request.source()).isEqualTo("skill-expert");
        assertThat(request.type()).isEqualTo("MINI_APP_EVENT");
        assertThat(request.deepLink()).isEqualTo("nmi://miniapp/skill-expert");
    }

    @Test
    void eachStepReadsNaturally() {
        assertThat(BookingNotificationGateway.request(event(Step.OFFERED, 1), COLOMBO).body())
                .isEqualTo("Viraj sent a price for Leak repair (LKR 2,500.00) on Oct 9, 10:00 AM. Review and accept it to confirm.");
        assertThat(BookingNotificationGateway.request(event(Step.CONFIRMED, 0), COLOMBO).title())
                .isEqualTo("Booking confirmed");
        assertThat(BookingNotificationGateway.request(event(Step.CONFIRMED, 0), COLOMBO).body())
                .isEqualTo("Viraj confirmed Leak repair on Oct 9, 10:00 AM. Pay LKR 2,500.00 to secure it.");
        assertThat(BookingNotificationGateway.request(event(Step.OFFER_ACCEPTED, 0), COLOMBO).body())
                .isEqualTo("Dilshan accepted your offer for Leak repair (LKR 2,500.00) on Oct 9, 10:00 AM.");
        assertThat(BookingNotificationGateway.request(event(Step.DECLINED, 0), COLOMBO).body())
                .isEqualTo("Viraj can't take Leak repair on Oct 9, 10:00 AM. Try another expert.");
        assertThat(BookingNotificationGateway.request(event(Step.CANCELLED, 0), COLOMBO).body())
                .isEqualTo("Dilshan cancelled Leak repair on Oct 9, 10:00 AM.");
        assertThat(BookingNotificationGateway.request(event(Step.PAID, 0), COLOMBO).body())
                .isEqualTo("Dilshan paid LKR 2,500.00 for Leak repair on Oct 9, 10:00 AM.");
        assertThat(BookingNotificationGateway.request(event(Step.COMPLETED, 0), COLOMBO).body())
                .isEqualTo("Viraj marked Leak repair as done. Rate your experience.");
    }

    @Test
    void pricesReadTheSameWhateverWasTyped() {
        assertThat(BookingNotificationGateway.formatPrice("2500")).isEqualTo("LKR 2,500.00");
        assertThat(BookingNotificationGateway.formatPrice("LKR 25,000")).isEqualTo("LKR 25,000.00");
        assertThat(BookingNotificationGateway.formatPrice("Rs. 1500.5")).isEqualTo("LKR 1,500.50");
        assertThat(BookingNotificationGateway.formatPrice("Free")).isEqualTo("Free");
        assertThat(BookingNotificationGateway.formatPrice("  ")).isNull();
        assertThat(BookingNotificationGateway.formatPrice(null)).isNull();
    }

    @Test
    void anUnpricedBookingLeavesTheAmountOut() {
        BookingNotificationEvent unpriced = new BookingNotificationEvent(Step.CONFIRMED, 42L, "user-1", "Dilshan",
                "Viraj", "Leak repair", null, VISIT, 0);

        assertThat(BookingNotificationGateway.request(unpriced, COLOMBO).body())
                .isEqualTo("Viraj confirmed Leak repair on Oct 9, 10:00 AM.");
    }

    @Test
    void aRevisedOfferIsNotDedupedAgainstTheFirst() {
        gateway("secret").onBookingStep(event(Step.OFFERED, 1700000000000L));

        verify(client).createInApp(eq("booking-42-OFFERED-1700000000000"), any());
    }

    @Test
    void sendsOncePerBookingStep() {
        gateway("secret").onBookingStep(event(Step.CONFIRMED, 0));

        ArgumentCaptor<InAppNotificationRequest> request = ArgumentCaptor.forClass(InAppNotificationRequest.class);
        verify(client).createInApp(eq("booking-42-CONFIRMED"), request.capture());
        assertThat(request.getValue().userId()).isEqualTo("user-1");
    }

    @Test
    void withoutSecretNothingIsSent() {
        gateway("").onBookingStep(event(Step.REQUESTED, 0));

        verifyNoInteractions(client);
    }

    @Test
    void notificationFailureNeverEscapes() {
        doThrow(new IllegalStateException("notification-service down")).when(client).createInApp(anyString(), any());

        gateway("secret").onBookingStep(event(Step.PAID, 0));

        verify(client).createInApp(anyString(), any());
    }
}
