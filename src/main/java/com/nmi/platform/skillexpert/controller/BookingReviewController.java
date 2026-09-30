/**
 * Public client reviews. Only the customer rates the expert after a job.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nmi.platform.skillexpert.model.dto.PartyReviewResponse;
import com.nmi.platform.skillexpert.model.dto.ReviewPageResponse;
import com.nmi.platform.skillexpert.model.dto.SubmitBookingReviewRequest;
import com.nmi.platform.skillexpert.security.CurrentUser;
import com.nmi.platform.skillexpert.service.BookingReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/skill-experts")
@Validated
public class BookingReviewController {

    private static final int MAX_PAGE_SIZE = 20;

    private final BookingReviewService service;
    private final CurrentUser currentUser;

    public BookingReviewController(BookingReviewService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/bookings/{id}/reviews")
    public PartyReviewResponse submit(@PathVariable Long id, @Valid @RequestBody SubmitBookingReviewRequest request) {
        return service.submit(currentUser.requireUserId(), currentUser.identityKeys(), id, request);
    }

    @GetMapping("/reviews/mine")
    public List<PartyReviewResponse> mine() {
        currentUser.requireUserId();
        return service.mine(currentUser.identityKeys());
    }

    @GetMapping("/{id}/reviews")
    public ReviewPageResponse reviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        currentUser.requireUserId();
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        return service.publicReviews(
                id,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
