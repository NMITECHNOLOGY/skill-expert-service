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
    void expertCannotRequestThemselvesWhenOnlyTheTokenSubjectMatches() throws Exception {
        int profileId = approve("self-subject");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(jwt().jwt(builder -> builder
                                .subject("self-subject")
                                .claim("user_id", "different-account")
                                .claim("kyc_status", "VERIFIED")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot request yourself."));
    }

    @Test
    void expertCannotRequestThemselvesWhenTheUserIdOnlyDiffersByCase() throws Exception {
        int profileId = approve("Case-Expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(member("case-expert"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot request yourself."));
    }

    @Test
    void registeredExpertCannotBookSomeoneDuringTheirOwnJob() throws Exception {
        String when = future();
        int ownProfile = approve("dual-expert");
        int otherProfile = approve("other-trade");
        mockMvc.perform(post("/api/v1/skill-experts/" + ownProfile + "/bookings")
                        .with(customer("neighbour", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/skill-experts/" + otherProfile + "/bookings")
                        .with(member("dual-expert"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a job at that time."));
    }

    @Test
    void cannotBookAnExpertWhoIsOutOnTheirOwnVisit() throws Exception {
        String when = future();
        int visitor = approve("out-expert");
        int host = approve("home-expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + host + "/bookings")
                        .with(member("out-expert"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/skill-experts/" + visitor + "/bookings")
                        .with(customer("third-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This expert is already booked at that time."));

        mockMvc.perform(get("/api/v1/skill-experts/" + visitor + "/bookings/taken")
                        .with(customer("third-seeker", "VERIFIED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anExpertCanStillBookADifferentExpertAtAFreeTime() throws Exception {
        int otherProfile = approve("free-other");
        approve("free-self");
        mockMvc.perform(post("/api/v1/skill-experts/" + otherProfile + "/bookings")
                        .with(member("free-self"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", future())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
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
    void theSameExpertCannotBeBookedTwiceAtTheSameTime() throws Exception {
        String when = future();
        int profileId = approve("busy-expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("busy-seeker-a", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("busy-seeker-b", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This expert is already booked at that time."));

        mockMvc.perform(get("/api/v1/skill-experts/" + profileId + "/bookings/taken")
                        .with(customer("busy-seeker-b", "VERIFIED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aVisitInsideTheSameHourIsAlsoRejected() throws Exception {
        Instant start = Instant.now().plusSeconds(86_400);
        int profileId = approve("hour-expert");
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("hour-seeker-a", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", start.toString())))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("hour-seeker-b", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", start.plusSeconds(30 * 60).toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This expert is already booked at that time."));
    }

    @Test
    void onePersonCannotHoldTwoBookingsAtTheSameTime() throws Exception {
        String when = future();
        int first = approve("place-expert-a");
        int second = approve("place-expert-b");
        mockMvc.perform(post("/api/v1/skill-experts/" + first + "/bookings")
                        .with(customer("two-places", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/" + second + "/bookings")
                        .with(customer("two-places", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already have a booking at that time."));
    }

    @Test
    void cancellingOpensTheTimeAgain() throws Exception {
        String when = future();
        int profileId = approve("reopen-expert");
        MvcResult booked = mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("reopen-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isOk())
                .andReturn();
        int bookingId = JsonPath.parse(booked.getResponse().getContentAsString()).read("$.id", Integer.class);
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/cancel")
                        .with(customer("reopen-seeker", "VERIFIED")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("reopen-next", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", when)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void addressServiceAndHorizonAreChecked() throws Exception {
        int profileId = approve("rules-expert");
        String when = future();
        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("rules-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceTitle":"Leak repair","scheduledAt":"%s"}
                                """.formatted(when)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add the address where they should come."));

        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("rules-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceTitle":"Roof tiles","address":"12 Galle Road","scheduledAt":"%s"}
                                """.formatted(when)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick one of this expert's services."));

        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("rules-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceTitle":"Leak repair","price":"LKR 10","address":"12 Galle Road","scheduledAt":"%s"}
                                """.formatted(when)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That price does not match this service."));

        mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("rules-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leak repair", Instant.now().plusSeconds(40L * 24 * 60 * 60).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick a time within the next 30 days."));
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
    void expertFinishesAPricedBookingOnlyAfterTheCustomerPays() throws Exception {
        String expertId = "paid-done-expert";
        int profileId = approve(expertId);
        MvcResult booked = mockMvc.perform(post("/api/v1/skill-experts/" + profileId + "/bookings")
                        .with(customer("paid-done-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceTitle":"Leak repair","price":"LKR 2500","address":"12 Galle Road","scheduledAt":"%s"}
                                """.formatted(future())))
                .andExpect(status().isOk())
                .andReturn();
        int bookingId = JsonPath.parse(booked.getResponse().getContentAsString()).read("$.id", Integer.class);

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The customer has not paid yet."));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("paid-done-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-300\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/complete").with(member(expertId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void customerRecordsTheSuperAppPaymentAfterTheExpertConfirms() throws Exception {
        String expertId = "pay-expert";
        int profileId = approve(expertId);
        int bookingId = request(profileId, "pay-seeker");

        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("pay-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-100\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + bookingId + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("pay-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").value("PAY-100"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("pay-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-100\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").value("PAY-100"));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/cancel").with(customer("pay-seeker", "VERIFIED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This booking is already paid."));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("pay-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-OTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This booking is already paid."));
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("someone-else", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-100\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + bookingId + "/payment")
                        .with(customer("pay-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"\"}"))
                .andExpect(status().isBadRequest());

        int second = request(profileId, "pay-seeker");
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + second + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + second + "/payment")
                        .with(customer("pay-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-100\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This payment is already linked to a booking."));
    }

    @Test
    void declinedOrFinishedBookingsCannotBePaid() throws Exception {
        String expertId = "pay-closed-expert";
        int profileId = approve(expertId);

        int declined = request(profileId, "pay-closed-seeker");
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + declined + "/decline").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + declined + "/payment")
                        .with(customer("pay-closed-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-200\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pay only after the expert confirms the request."));

        int finished = request(profileId, "pay-closed-seeker");
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + finished + "/accept").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/skill-experts/me/bookings/" + finished + "/complete").with(member(expertId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + finished + "/payment")
                        .with(customer("pay-closed-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-201\"}"))
                .andExpect(status().isConflict());

        int cancelled = request(profileId, "pay-closed-seeker");
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + cancelled + "/cancel").with(customer("pay-closed-seeker", "VERIFIED")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/skill-experts/bookings/" + cancelled + "/payment")
                        .with(customer("pay-closed-seeker", "VERIFIED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentReference\":\"PAY-202\"}"))
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

    private int hoursAhead = 24;

    /** Each call is two hours later, so separate visits in one test do not overlap. */
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
