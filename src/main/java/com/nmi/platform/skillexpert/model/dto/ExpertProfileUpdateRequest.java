package com.nmi.platform.skillexpert.model.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Partial update. A null field is left unchanged. An empty list clears that collection.
 */
public record ExpertProfileUpdateRequest(
        @Size(max = 200) String displayName,
        @Size(max = 200) String jobTitle,
        @Size(max = 2000) String bio,
        String photoUri,
        List<@Size(max = 80) String> skills,
        @Valid List<PortfolioItemRequest> portfolio,
        @Valid List<ServiceItemRequest> services
) {
    public record PortfolioItemRequest(
            String mediaUri,
            @Size(max = 500) String caption
    ) {}

    public record ServiceItemRequest(
            @Size(max = 200) String title,
            @Size(max = 64) String price,
            @Size(max = 1000) String description
    ) {}
}
