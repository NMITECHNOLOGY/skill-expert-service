package com.nmi.platform.skillexpert.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nmi.platform.skillexpert.model.dto.AvailabilityRequest;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileResponse;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileSummaryResponse;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileUpdateRequest;
import com.nmi.platform.skillexpert.model.dto.ExpertReviewRequest;
import com.nmi.platform.skillexpert.security.CurrentUser;
import com.nmi.platform.skillexpert.service.ExpertProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/skill-experts")
@Validated
public class ExpertProfileController {

    private final ExpertProfileService service;
    private final CurrentUser currentUser;

    public ExpertProfileController(ExpertProfileService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/me/profile")
    public ExpertProfileResponse getMine() {
        return service.getMine(currentUser.requireUserId());
    }

    @PutMapping("/me/profile")
    public ExpertProfileResponse saveDraft(@Valid @RequestBody ExpertProfileUpdateRequest request) {
        return service.saveDraft(currentUser.requireUserId(), request);
    }

    @PostMapping("/me/profile/submit")
    public ExpertProfileResponse submit() {
        return service.submit(currentUser.requireUserId());
    }

    @PutMapping("/me/availability")
    public ExpertProfileResponse setAvailability(@Valid @RequestBody AvailabilityRequest request) {
        return service.setAvailability(currentUser.requireUserId(), Boolean.TRUE.equals(request.available()));
    }

    @GetMapping
    public Page<ExpertProfileSummaryResponse> listApproved(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listApproved(pageable);
    }

    @GetMapping("/{id}")
    public ExpertProfileResponse getApproved(@PathVariable Long id) {
        return service.getApproved(id);
    }

    @GetMapping("/admin/requests")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public Page<ExpertProfileSummaryResponse> adminList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.adminList(status, search, pageable);
    }

    @GetMapping("/admin/requests/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ExpertProfileResponse adminGet(@PathVariable Long id) {
        return service.adminGet(id);
    }

    @PostMapping("/admin/requests/{id}/approve")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ExpertProfileResponse approve(
            @PathVariable Long id,
            @RequestBody(required = false) ExpertReviewRequest request) {
        return service.approve(id, request, currentUser.optionalUsername());
    }

    @PostMapping("/admin/requests/{id}/reject")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ExpertProfileResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody ExpertReviewRequest.RejectRequest request) {
        return service.reject(id, request, currentUser.optionalUsername());
    }
}
