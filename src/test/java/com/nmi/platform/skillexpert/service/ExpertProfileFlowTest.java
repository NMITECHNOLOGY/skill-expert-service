package com.nmi.platform.skillexpert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.nmi.platform.skillexpert.repository.ExpertProfileRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ExpertProfileFlowTest {

    private static final String USER = "flow-user";

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

    @Autowired
    private ExpertProfileRepository repository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listingStaysLiveUntilAnUpdateIsApproved() throws Exception {
        mockMvc.perform(get("/api/v1/skill-experts/me/profile").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.liveListing").value(false));
        assertThat(repository.findByUserId(USER)).isEmpty();

        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member()))
                .andExpect(status().isBadRequest());

        MvcResult submitted = mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Nope\"}"))
                .andExpect(status().isConflict());

        int id = JsonPath.parse(submitted.getResponse().getContentAsString()).read("$.id", Integer.class);
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/approve")
                        .with(member()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/approve")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Welcome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.liveListing").value(true));

        mockMvc.perform(get("/api/v1/skill-experts").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.userId == '" + USER + "')].displayName").value(hasItem("Ada")));
        mockMvc.perform(get("/api/v1/skill-experts/" + id).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada"))
                .andExpect(jsonPath("$.liveListing").value(true));

        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.updateStatus").value("DRAFT"))
                .andExpect(jsonPath("$.displayName").value("Ada Updated"))
                .andExpect(jsonPath("$.liveDisplayName").value("Ada"))
                .andExpect(jsonPath("$.liveListing").value(true));

        mockMvc.perform(get("/api/v1/skill-experts/" + id).with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada"));

        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateStatus").value("PENDING"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/skill-experts/" + id).with(member()))
                .andExpect(jsonPath("$.displayName").value("Ada"));
        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Blocked\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/skill-experts/admin/requests")
                        .with(admin())
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.userId == '" + USER + "')].displayName").value(hasItem("Ada Updated")))
                .andExpect(jsonPath("$.content[?(@.userId == '" + USER + "')].updateStatus").value(hasItem("PENDING")));

        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.displayName").value("Ada Updated"));
        mockMvc.perform(get("/api/v1/skill-experts/" + id).with(member()))
                .andExpect(jsonPath("$.displayName").value("Ada Updated"));

        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada Rejected\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateStatus").value("PENDING"));
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/reject")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Use your public name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.updateStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Use your public name"));
        mockMvc.perform(get("/api/v1/skill-experts/" + id).with(member()))
                .andExpect(jsonPath("$.displayName").value("Ada Updated"));
        mockMvc.perform(get("/api/v1/skill-experts/me/profile").with(member()))
                .andExpect(jsonPath("$.displayName").value("Ada Rejected"))
                .andExpect(jsonPath("$.liveDisplayName").value("Ada Updated"))
                .andExpect(jsonPath("$.updateStatus").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Use your public name"));

        // Rejected update: expert can edit and submit again.
        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada Final\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateStatus").value("DRAFT"));
        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateStatus").value("PENDING"));
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Final"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/skill-experts/" + id).with(member()))
                .andExpect(jsonPath("$.displayName").value("Ada Final"));
    }

    @Test
    void firstSubmissionCanBeRejectedThenResubmitted() throws Exception {
        String user = "reject-resubmit-user";
        RequestPostProcessor member = jwt().jwt(builder -> builder
                .subject(user)
                .claim("user_id", user)
                .claim("kyc_status", "VERIFIED"));

        MvcResult saved = mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE))
                .andExpect(status().isOk())
                .andReturn();
        int id = JsonPath.parse(saved.getResponse().getContentAsString()).read("$.id", Integer.class);

        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/reject")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Add a clearer bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Add a clearer bio"));

        mockMvc.perform(get("/api/v1/skill-experts").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.userId == '" + user + "')]").isEmpty());

        mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bio": "Clear bio about wiring and repairs for homes."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.liveListing").value(true));
    }

    @Test
    void expertCanTurnAvailabilityOffWithoutANewReview() throws Exception {
        RequestPostProcessor member = memberAs("avail-user");
        MvcResult saved = mockMvc.perform(put("/api/v1/skill-experts/me/profile")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPLETE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andReturn();
        int id = JsonPath.parse(saved.getResponse().getContentAsString()).read("$.id", Integer.class);
        mockMvc.perform(post("/api/v1/skill-experts/me/profile/submit").with(member))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/admin/requests/" + id + "/approve").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(put("/api/v1/skill-experts/me/availability")
                        .with(member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/skill-experts").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.userId == 'avail-user')].available").value(hasItem(false)));

        String when = java.time.Instant.now().plusSeconds(86_400).toString();
        mockMvc.perform(post("/api/v1/skill-experts/" + id + "/bookings")
                        .with(memberAs("someone-else"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceTitle":"Leak repair","scheduledAt":"%s"}
                                """.formatted(when)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This expert is not free right now."));
    }

    @Test
    void customerRequestShowsOnTheExpertBookings() throws Exception {
        RequestPostProcessor expert = memberAs("book-expert");
        RequestPostProcessor customer = jwt().jwt(builder -> builder
                .subject("book-customer")
                .claim("user_id", "book-customer")
                .claim("given_name", "Nimal")
                .claim("family_name", "Perera")
                .claim("kyc_status", "VERIFIED"));

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

        String when = java.time.Instant.now().plusSeconds(86_400).toString();
        MvcResult booked = mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceTitle": "Leak repair",
                                  "price": "LKR 2500",
                                  "address": "Park Road",
                                  "note": "Kitchen leak",
                                  "scheduledAt": "%s"
                                }
                                """.formatted(when)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.customerName").value("Nimal Perera"))
                .andReturn();
        int bookingId = JsonPath.parse(booked.getResponse().getContentAsString()).read("$.id", Integer.class);

        mockMvc.perform(get("/api/v1/skill-experts/me/bookings").with(expert))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceTitle").value("Leak repair"))
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(expert))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/skill-experts/bookings/mine").with(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].expertName").value("Ada"));
    }

    private static RequestPostProcessor member() {
        return memberAs(USER);
    }

    private static RequestPostProcessor memberAs(String userId) {
        return jwt().jwt(builder -> builder
                .subject(userId)
                .claim("user_id", userId)
                .claim("kyc_status", "VERIFIED"));
    }

    private static RequestPostProcessor admin() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("USER_UPDATE"))
                .jwt(builder -> builder.subject("admin").claim("preferred_username", "admin"));
    }
}
