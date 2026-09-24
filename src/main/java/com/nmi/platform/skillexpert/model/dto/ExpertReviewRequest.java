package com.nmi.platform.skillexpert.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpertReviewRequest(
        @Size(max = 2000) String note
) {
    public record RejectRequest(
            @NotBlank @Size(max = 2000) String note
    ) {}
}
