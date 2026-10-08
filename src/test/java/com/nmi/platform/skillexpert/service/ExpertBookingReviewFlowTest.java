/**
 * Customer ratings after a finished booking. Experts cannot rate the customer.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.service;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ExpertBookingReviewFlowTest {

    private static final String COMPLETE = """
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

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void onlyTheCustomerOnAFinishedJobCanRateItOnce() throws Exception {
        String expertId = "rate-expert";
        int profileId = approve(expertId);
        int bookingId = request(profileId, "rate-seeker");

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("rate-seeker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":5,\"comment\":\"Great\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can rate this after the job is done."));

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        pay(bookingId, "rate-seeker");
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("stranger"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found"));

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("rate-seeker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("rate-seeker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"No stars\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("rate-seeker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":5,\"comment\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("rate-seeker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":5,\"comment\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("FINDER_TO_EXPERT"))
                .andExpect(jsonPath("$.stars").value(5))
                .andExpect(jsonPath("$.authorName").value("Nimal Perera"))
                .andExpect(jsonPath("$.comment").doesNotExist());

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(customer("rate-seeker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":4,\"comment\":\"Changed my mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already rated this job."));

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/reviews")
                        .with(member(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":4,\"comment\":\"Careful client\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only the customer can rate this job."));

        mockMvc.perform(get("/api/v1/skill-experts/" + profileId + "/reviews").with(customer("rate-seeker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.rating").value(5.0))
                .andExpect(jsonPath("$.summary.reviewCount").value(1))
                .andExpect(jsonPath("$.summary.fiveStars").value(1))
                .andExpect(jsonPath("$.summary.completedJobs").value(1))
                .andExpect(jsonPath("$.summary.responseRate").value(100))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].authorName").value("Nimal Perera"))
                .andExpect(jsonPath("$.content[0].serviceTitle").value("Leak repair"))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/v1/skill-experts/" + profileId).with(customer("rate-seeker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.completedJobs").value(1))
                .andExpect(jsonPath("$.responseRate").value(100));

        mockMvc.perform(get("/api/v1/skill-experts/reviews/mine").with(customer("rate-seeker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].direction").value("FINDER_TO_EXPERT"));
        mockMvc.perform(get("/api/v1/skill-experts/reviews/mine").with(member(expertId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].direction").value("FINDER_TO_EXPERT"));
        mockMvc.perform(get("/api/v1/skill-experts/reviews/mine").with(customer("stranger")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publicScoreUsesCustomerRatingsAndUnansweredRequests() throws Exception {
        String expertId = "score-expert";
        int profileId = approve(expertId);
        int first = finish(expertId, profileId, "score-a");
        int second = finish(expertId, profileId, "score-b");
        request(profileId, "score-c");
        int cancelled = request(profileId, "score-d");
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + cancelled + "/cancel").with(customer("score-d")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + first + "/reviews")
                        .with(customer("score-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":5,\"comment\":\"On time\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + second + "/reviews")
                        .with(customer("score-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":4,\"comment\":\"Good\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + first + "/reviews")
                        .with(member(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\":1,\"comment\":\"Late\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/skill-experts/" + profileId + "/reviews").with(customer("score-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.rating").value(4.5))
                .andExpect(jsonPath("$.summary.reviewCount").value(2))
                .andExpect(jsonPath("$.summary.fiveStars").value(1))
                .andExpect(jsonPath("$.summary.fourStars").value(1))
                .andExpect(jsonPath("$.summary.completedJobs").value(2))
                .andExpect(jsonPath("$.summary.responseRate").value(67))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.stars == 4)].authorName", hasItem("Nimal Perera")))
                .andExpect(jsonPath("$.content[?(@.stars == 5)].stars", hasItem(5)));

        mockMvc.perform(get("/api/v1/skill-experts?page=0&size=100").with(customer("score-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + profileId + ")].rating", hasItem(4.5)))
                .andExpect(jsonPath("$.content[?(@.id == " + profileId + ")].reviewCount", hasItem(2)));
    }

    @Test
    void aNewExpertHasNoScoreUntilSomeoneAnswers() throws Exception {
        int profileId = approve("fresh-expert");
        mockMvc.perform(get("/api/v1/skill-experts/" + profileId + "/reviews").with(customer("fresh-seeker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.rating").doesNotExist())
                .andExpect(jsonPath("$.summary.reviewCount").value(0))
                .andExpect(jsonPath("$.summary.responseRate").doesNotExist())
                .andExpect(jsonPath("$.summary.completedJobs").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/v1/skill-experts/404040/reviews").with(customer("fresh-seeker")))
                .andExpect(status().isNotFound());
    }

    private int finish(String expertId, int profileId, String customerId) throws Exception {
        int bookingId = request(profileId, customerId);
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        pay(bookingId, customerId);
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isOk());
        return bookingId;
    }

    private int payments;

    /** Listed services carry the expert's price, so a job is paid before it can be finished. */
    private void pay(int bookingId, String customerId) throws Exception {
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-REVIEW-" + bookingId + "-" + (++payments) + "\"}"))
                .andExpect(status().isOk());
    }

    private int approve(String userId) throws Exception {
        RequestPostProcessor expert = member(userId);
        MvcResult saved = mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(expert)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE))
                .andExpect(status().isOk())
                .andReturn();
        int profileId = JsonPath.parse(saved.getResponse().getContentAsString()).read("$.id", Integer.class);
        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(expert))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + profileId + "/approve").with(admin()))
                .andExpect(status().isOk());
        return profileId;
    }

    private int request(int profileId, String customerId) throws Exception {
        MvcResult booked = mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer(customerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.parse(booked.getResponse().getContentAsString()).read("$.id", Integer.class);
    }

    private int hoursAhead = 24;

    private String future() {
        hoursAhead += 2;
        return Instant.now().plus(java.time.Duration.ofHours(hoursAhead)).toString();
    }

    private static String body(String serviceTitle, String when) {
        return """
                {"serviceTitle":"%s","address":"12 Galle Road","scheduledAt":"%s"}
                """.formatted(serviceTitle, when);
    }

    private static RequestPostProcessor member(String userId) {
        return customer(userId);
    }

    private static RequestPostProcessor customer(String userId) {
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
