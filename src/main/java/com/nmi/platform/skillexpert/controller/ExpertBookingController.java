package com.nmi.platform.skillexpert.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nmi.platform.skillexpert.model.dto.BookingResponse;
import com.nmi.platform.skillexpert.model.dto.CreateBookingRequest;
import com.nmi.platform.skillexpert.model.dto.RecordBookingPaymentRequest;
import com.nmi.platform.skillexpert.security.CurrentUser;
import com.nmi.platform.skillexpert.service.ExpertBookingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/skill-experts")
@Validated
public class ExpertBookingController {

    private final ExpertBookingService service;
    private final CurrentUser currentUser;

    public ExpertBookingController(ExpertBookingService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/{profileId}/bookings")
    public BookingResponse create(@PathVariable Long profileId, @Valid @RequestBody CreateBookingRequest request) {
        return service.create(
                currentUser.requireUserId(),
                currentUser.identityKeys(),
                currentUser.displayName(),
                profileId,
                request);
    }

    @GetMapping("/{profileId}/bookings/taken")
    public List<Instant> taken(@PathVariable Long profileId) {
        currentUser.requireUserId();
        return service.takenTimes(profileId);
    }

    @GetMapping("/bookings/mine")
    public List<BookingResponse> mine() {
        return service.listForCustomer(currentUser.requireUserId());
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id) {
        return service.cancel(currentUser.requireUserId(), id);
    }

    @PostMapping("/bookings/{id}/payment")
    public BookingResponse recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody RecordBookingPaymentRequest request) {
        return service.recordPayment(currentUser.requireUserId(), id, request);
    }

    @GetMapping("/me/bookings")
    public List<BookingResponse> incoming() {
        return service.listForExpert(currentUser.requireUserId());
    }

    @PutMapping("/me/bookings/{id}/accept")
    public BookingResponse accept(@PathVariable Long id) {
        return service.accept(currentUser.requireUserId(), id);
    }

    @PutMapping("/me/bookings/{id}/decline")
    public BookingResponse decline(@PathVariable Long id) {
        return service.decline(currentUser.requireUserId(), id);
    }

    @PutMapping("/me/bookings/{id}/complete")
    public BookingResponse complete(@PathVariable Long id) {
        return service.complete(currentUser.requireUserId(), id);
    }
}
