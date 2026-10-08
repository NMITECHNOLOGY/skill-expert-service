/**
 * Who hears about each booking step, across every path through a booking.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-10-08
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.nmi.platform.skillexpert.integration.notification.BookingNotificationEvent;
import com.nmi.platform.skillexpert.integration.notification.BookingNotificationEvent.Step;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class BookingNotificationScenariosTest {

    private static final String PROFILE = """
            {
              "displayName": "Ada",
              "jobTitle": "Plumber",
              "bio": "Ten years of plumbing work.",
              "photoUri": "https://cdn.example/ada.jpg",
              "skills": ["Pipes"],
              "portfolio": [{"mediaUri": "https://cdn.example/work.jpg", "caption": "Kitchen"}],
              "services": [{"title": "Leak repair", "price": "LKR 2500", "description": "Same day"}]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private int hoursAhead = 24;

    @BeforeEach
    void forgetSetupEvents() {
        events.clear();
    }

    @Test
    void listedServiceFromRequestToDone() throws Exception {
        int profile = approve("n-expert-1");
        int id = requestListed(profile, "n-seeker-1");
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/accept"), "n-expert-1");
        pay(id, "n-seeker-1", "PAY-N1");
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/complete"), "n-expert-1");

        assertSteps(
                tuple(Step.REQUESTED, "n-expert-1"),
                tuple(Step.CONFIRMED, "n-seeker-1"),
                tuple(Step.PAID, "n-expert-1"),
                tuple(Step.COMPLETED, "n-seeker-1"));
        assertThat(sent()).allSatisfy(event -> {
            assertThat(event.bookingId()).isEqualTo((long) id);
            assertThat(event.customerName()).isEqualTo("Nimal Perera");
            assertThat(event.expertName()).isEqualTo("Ada");
            assertThat(event.service()).isEqualTo("Leak repair");
        });
    }

    @Test
    void customJobWithARevisedOffer() throws Exception {
        int profile = approve("n-expert-2");
        int id = requestCustom(profile, "n-seeker-2");
        propose(id, "n-expert-2", "LKR 4500");
        propose(id, "n-expert-2", "LKR 4000");
        act(post("/api/v1/skill-experts/bookings/" + id + "/accept-proposal"), "n-seeker-2");
        pay(id, "n-seeker-2", "PAY-N2");

        assertSteps(
                tuple(Step.REQUESTED, "n-expert-2"),
                tuple(Step.OFFERED, "n-seeker-2"),
                tuple(Step.OFFERED, "n-seeker-2"),
                tuple(Step.OFFER_ACCEPTED, "n-expert-2"),
                tuple(Step.PAID, "n-expert-2"));
        List<BookingNotificationEvent> offers = sent().stream().filter(e -> e.step() == Step.OFFERED).toList();
        assertThat(offers).extracting(BookingNotificationEvent::price).containsExactly("LKR 4500", "LKR 4000");
        // Each offer is its own notification, not deduplicated against the first.
        assertThat(offers.get(0).version()).isNotZero().isNotEqualTo(offers.get(1).version());
    }

    @Test
    void declinedRequestTellsOnlyTheCustomer() throws Exception {
        int profile = approve("n-expert-3");
        int id = requestListed(profile, "n-seeker-3");
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/decline"), "n-expert-3");

        assertSteps(tuple(Step.REQUESTED, "n-expert-3"), tuple(Step.DECLINED, "n-seeker-3"));
    }

    @Test
    void cancellingTellsTheExpertAtEveryStage() throws Exception {
        int profile = approve("n-expert-4");
        int waiting = requestListed(profile, "n-seeker-4");
        act(post("/api/v1/skill-experts/bookings/" + waiting + "/cancel"), "n-seeker-4");

        int offered = requestCustom(profile, "n-seeker-4");
        propose(offered, "n-expert-4", "LKR 3000");
        act(post("/api/v1/skill-experts/bookings/" + offered + "/cancel"), "n-seeker-4");

        int confirmed = requestListed(profile, "n-seeker-4");
        act(put("/api/v1/skill-experts/me/bookings/" + confirmed + "/accept"), "n-expert-4");
        act(post("/api/v1/skill-experts/bookings/" + confirmed + "/cancel"), "n-seeker-4");

        assertSteps(
                tuple(Step.REQUESTED, "n-expert-4"),
                tuple(Step.CANCELLED, "n-expert-4"),
                tuple(Step.REQUESTED, "n-expert-4"),
                tuple(Step.OFFERED, "n-seeker-4"),
                tuple(Step.CANCELLED, "n-expert-4"),
                tuple(Step.REQUESTED, "n-expert-4"),
                tuple(Step.CONFIRMED, "n-seeker-4"),
                tuple(Step.CANCELLED, "n-expert-4"));
    }

    @Test
    void retriesAndRejectedActionsSendNothingMore() throws Exception {
        int profile = approve("n-expert-5");
        int id = requestListed(profile, "n-seeker-5");
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/accept"), "n-expert-5");
        events.clear();

        // Paying before is fine; the same reference again is a quiet retry.
        pay(id, "n-seeker-5", "PAY-N5");
        pay(id, "n-seeker-5", "PAY-N5");
        // Already confirmed, already paid, or someone else's booking: refused, nothing sent.
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/accept"), "n-expert-5", status().isConflict());
        act(post("/api/v1/skill-experts/bookings/" + id + "/cancel"), "n-seeker-5", status().isConflict());
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/decline"), "n-expert-5", status().isConflict());
        act(post("/api/v1/skill-experts/bookings/" + id + "/cancel"), "n-stranger-5", status().isNotFound());
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/complete"), "n-stranger-5", status().isNotFound());

        assertSteps(tuple(Step.PAID, "n-expert-5"));
    }

    @Test
    void finishingBeforePaymentIsRefusedQuietly() throws Exception {
        int profile = approve("n-expert-6");
        int id = requestListed(profile, "n-seeker-6");
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/accept"), "n-expert-6");
        events.clear();

        act(put("/api/v1/skill-experts/me/bookings/" + id + "/complete"), "n-expert-6", status().isConflict());

        assertThat(sent()).isEmpty();
    }

    @Test
    void anExpertBookingAnotherExpertIsTheCustomerThere() throws Exception {
        int first = approve("n-expert-7a");
        approve("n-expert-7b");
        int id = requestListed(first, "n-expert-7b");
        act(put("/api/v1/skill-experts/me/bookings/" + id + "/accept"), "n-expert-7a");

        // 7b is an expert too, but on this booking 7b is the customer and hears the customer side.
        assertSteps(tuple(Step.REQUESTED, "n-expert-7a"), tuple(Step.CONFIRMED, "n-expert-7b"));
    }

    private void assertSteps(org.assertj.core.groups.Tuple... expected) {
        assertThat(sent())
                .extracting(BookingNotificationEvent::step, BookingNotificationEvent::recipient)
                .containsExactly(expected);
    }

    private List<BookingNotificationEvent> sent() {
        return events.stream(BookingNotificationEvent.class).toList();
    }

    private int approve(String userId) throws Exception {
        MvcResult saved = mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(user(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROFILE))
                .andExpect(status().isOk())
                .andReturn();
        int profileId = JsonPath.parse(saved.getResponse().getContentAsString()).read("$.id", Integer.class);
        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(user(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + profileId + "/approve").with(admin()))
                .andExpect(status().isOk());
        events.clear();
        return profileId;
    }

    private int requestListed(int profileId, String customerId) throws Exception {
        return book(profileId, customerId, """
                {"serviceTitle":"Leak repair","price":"LKR 2500","address":"12 Galle Road","scheduledAt":"%s"}
                """.formatted(future()));
    }

    private int requestCustom(int profileId, String customerId) throws Exception {
        return book(profileId, customerId, """
                {"serviceTitle":"Kitchen tap","note":"Leaking since yesterday","address":"12 Galle Road",
                 "scheduledAt":"%s","custom":true}
                """.formatted(future()));
    }

    private int book(int profileId, String customerId, String body) throws Exception {
        MvcResult booked = mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(user(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.parse(booked.getResponse().getContentAsString()).read("$.id", Integer.class);
    }

    private void propose(int id, String expertId, String price) throws Exception {
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + id + "/proposal")
                        .with(user(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":"%s","note":"Washer and trap check.","scheduledAt":"%s"}
                                """.formatted(price, future())))
                .andExpect(status().isOk());
    }

    private void pay(int id, String customerId, String reference) throws Exception {
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + id + "/payment")
                        .with(user(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"" + reference + "\"}"))
                .andExpect(status().isOk());
    }

    private void act(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String userId)
            throws Exception {
        act(request, userId, status().isOk());
    }

    private void act(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String userId,
                     ResultMatcher expected) throws Exception {
        mockMvc.perform(request.with(user(userId))).andExpect(expected);
    }

    /** Each call is two hours later, so visits in one test never overlap. */
    private String future() {
        hoursAhead += 2;
        return Instant.now().plus(Duration.ofHours(hoursAhead)).toString();
    }

    private static RequestPostProcessor user(String userId) {
        return jwt().jwt(builder -> builder
                .subject(userId)
                .claim("user_id", userId)
                .claim("given_name", "Nimal")
                .claim("family_name", "Perera")
                .claim("kyc_status", "VERIFIED"));
    }

    private static RequestPostProcessor admin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("USER_UPDATE"))
                .jwt(builder -> builder.subject("admin").claim("preferred_username", "admin"));
    }
}
