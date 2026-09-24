package com.nmi.platform.skillexpert.service;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ExpertBookingScenariosTest {

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
    void unverifiedCustomerCanRequestAnApprovedExpert() throws Exception {
        int profileId = approve("open-expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("open-seeker", "PENDING"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.expertName").value("Ada"));
    }

    @Test
    void expertCannotRequestThemselves() throws Exception {
        String user = "self-expert";
        int profileId = approve(user);
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(member(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot request yourself."));
    }

    @Test
    void pastTimeIsRejected() throws Exception {
        int profileId = approve("future-expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("future-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", Instant.now().minusSeconds(120).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a future time."));
    }

    @Test
    void onlyAnApprovedListingCanBeRequested() throws Exception {
        RequestPostProcessor expert = member("draft-expert");
        MvcResult saved = mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(expert)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE))
                .andExpect(status().isOk())
                .andReturn();
        int profileId = JsonPath.parse(saved.getResponse().getContentAsString()).read("$.id", Integer.class);

        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("draft-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Expert profile not found"));

        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(expert))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("draft-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/skill-experts/999999/bookings")
                        .with(customer("draft-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Expert profile not found"));
    }

    @Test
    void blankServiceIsRejectedAndAnonymousCallersAreTurnedAway() throws Exception {
        int profileId = approve("guard-expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("guard-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ", future())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void declineBlocksLaterAcceptAndComplete() throws Exception {
        String expertId = "decline-expert";
        int profileId = approve(expertId);
        int bookingId = request(profileId, "decline-seeker");

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/decline").with(member(expertId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member(expertId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request is no longer waiting for that step."));
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/cancel").with(customer("decline-seeker", "VERIFIED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This request can no longer be cancelled."));
    }

    @Test
    void confirmedJobCanBeFinishedAndThenCannotBeCancelled() throws Exception {
        String expertId = "done-expert";
        int profileId = approve(expertId);
        int bookingId = request(profileId, "done-seeker");

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member(expertId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/cancel").with(customer("done-seeker", "VERIFIED")))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/decline").with(member(expertId)))
                .andExpect(status().isConflict());
    }

    @Test
    void customerCanCancelBeforeTheJobIsFinished() throws Exception {
        String expertId = "cancel-expert";
        int profileId = approve(expertId);
        int requested = request(profileId, "cancel-seeker");
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + requested + "/cancel").with(customer("cancel-seeker", "VERIFIED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + requested + "/accept").with(member(expertId)))
                .andExpect(status().isConflict());

        int confirmed = request(profileId, "cancel-seeker");
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + confirmed + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + confirmed + "/cancel").with(customer("cancel-seeker", "VERIFIED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void anotherPersonCannotActOnSomeoneElsesBooking() throws Exception {
        int profileId = approve("owner-expert");
        int bookingId = request(profileId, "owner-seeker");

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member("other-expert")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found"));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/cancel").with(customer("other-seeker", "VERIFIED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Booking not found"));
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/999999/accept").with(member("owner-expert")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsStayWithTheRightPerson() throws Exception {
        int profileId = approve("list-expert");
        request(profileId, "list-seeker");
        approve("other-list-expert");

        mockMvc.perform(get("/api/v1/skill-experts/me/bookings").with(member("list-expert")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerUserId").value("list-seeker"));
        mockMvc.perform(get("/api/v1/skill-experts/me/bookings").with(member("other-list-expert")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/skill-experts/bookings/mine").with(customer("list-seeker", "VERIFIED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expertName").value("Ada"));
        mockMvc.perform(get("/api/v1/skill-experts/bookings/mine").with(customer("stranger-seeker", "VERIFIED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unverifiedExpertCannotReplyToARequest() throws Exception {
        int profileId = approve("kyc-expert");
        int bookingId = request(profileId, "kyc-seeker");
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept")
                        .with(customer("kyc-expert", "PENDING")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KYC_NOT_VERIFIED"));
    }

    @Test
    void turningAvailableBackOnAllowsRequestsAgain() throws Exception {
        String expertId = "toggle-expert";
        int profileId = approve(expertId);
        mockMvc.perform(put("/api/v1/skill-experts/me/availability")
                        .with(member(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("toggle-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/skill-experts/me/availability")
                        .with(member(expertId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("toggle-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
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
                        .with(customer(customerId, "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.parse(booked.getResponse().getContentAsString()).read("$.id", Integer.class);
    }

    private static String future() {
        return Instant.now().plusSeconds(86_400).toString();
    }

    private static String body(String serviceTitle, String when) {
        return """
                {"serviceTitle":"%s","scheduledAt":"%s"}
                """.formatted(serviceTitle, when);
    }

    private static RequestPostProcessor member(String userId) {
        return customer(userId, "VERIFIED");
    }

    private static RequestPostProcessor customer(String userId, String kycStatus) {
        return jwt().jwt(builder -> builder
                .subject(userId)
                .claim("user_id", userId)
                .claim("given_name", "Nimal")
                .claim("family_name", "Perera")
                .claim("kyc_status", kycStatus));
    }

    private static RequestPostProcessor admin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("USER_UPDATE"))
                .jwt(builder -> builder.subject("admin").claim("preferred_username", "admin"));
    }
}
