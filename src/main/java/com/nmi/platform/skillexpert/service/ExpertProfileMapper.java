package com.nmi.platform.skillexpert.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileResponse;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileSummaryResponse;
import com.nmi.platform.skillexpert.model.entity.ExpertPortfolioItem;
import com.nmi.platform.skillexpert.model.entity.ExpertProfile;
import com.nmi.platform.skillexpert.model.entity.ExpertProfileRevision;
import com.nmi.platform.skillexpert.model.entity.ExpertRevisionPortfolioItem;
import com.nmi.platform.skillexpert.model.entity.ExpertRevisionServiceItem;
import com.nmi.platform.skillexpert.model.entity.ExpertServiceItem;
import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ExpertProfileMapper {

    private final ObjectMapper objectMapper;

    public ExpertProfileMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExpertProfileResponse emptyMine(String userId) {
        return new ExpertProfileResponse(
                null,
                userId,
                null,
                null,
                null,
                null,
                List.of(),
                ExpertProfileStatus.NOT_STARTED,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                List.of(),
                false,
                null,
                null,
                null,
                false
        );
    }

    /** Editable view for the signed-in expert. A revision replaces the form fields; the live row stays public. */
    public ExpertProfileResponse toMine(ExpertProfile profile) {
        ExpertProfileRevision revision = profile.getRevision();
        if (profile.getStatus() == ExpertProfileStatus.APPROVED && revision != null) {
            List<String> skills = readSkills(revision.getSkillsJson());
            String note = revision.getStatus() == ExpertProfileStatus.REJECTED ? revision.getReviewNote() : null;
            return response(
                    profile.getId(),
                    profile.getUserId(),
                    revision.getDisplayName(),
                    revision.getJobTitle(),
                    revision.getBio(),
                    revision.getPhotoUri(),
                    skills,
                    ExpertProfileStatus.APPROVED,
                    note,
                    revision.getSubmittedAt(),
                    revision.getReviewedAt(),
                    revision.getReviewedBy(),
                    completion(revision.getPhotoUri(), revision.getBio(), skills, revision.getPortfolio(), revision.getServices()),
                    revisionPortfolio(revision),
                    revisionServices(revision),
                    true,
                    revision.getStatus(),
                    profile.getDisplayName(),
                    profile.getJobTitle(),
                    profile.isAvailable()
            );
        }
        List<String> skills = readSkills(profile.getSkillsJson());
        String note = profile.getStatus() == ExpertProfileStatus.REJECTED ? profile.getReviewNote() : null;
        boolean live = profile.getStatus() == ExpertProfileStatus.APPROVED;
        return response(
                profile.getId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getJobTitle(),
                profile.getBio(),
                profile.getPhotoUri(),
                skills,
                profile.getStatus(),
                note,
                profile.getSubmittedAt(),
                profile.getReviewedAt(),
                profile.getReviewedBy(),
                completion(profile.getPhotoUri(), profile.getBio(), skills, profile.getPortfolio(), profile.getServices()),
                profilePortfolio(profile),
                profileServices(profile),
                live,
                null,
                null,
                null,
                profile.isAvailable()
        );
    }

    /** What customers see. Revisions are ignored. */
    public ExpertProfileResponse toPublic(ExpertProfile profile) {
        List<String> skills = readSkills(profile.getSkillsJson());
        return response(
                profile.getId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getJobTitle(),
                profile.getBio(),
                profile.getPhotoUri(),
                skills,
                profile.getStatus(),
                null,
                profile.getSubmittedAt(),
                profile.getReviewedAt(),
                profile.getReviewedBy(),
                completion(profile.getPhotoUri(), profile.getBio(), skills, profile.getPortfolio(), profile.getServices()),
                profilePortfolio(profile),
                profileServices(profile),
                profile.getStatus() == ExpertProfileStatus.APPROVED,
                null,
                null,
                null,
                profile.isAvailable()
        );
    }

    /** Admin review. A pending or rejected update is shown in place of the live fields. */
    public ExpertProfileResponse toAdmin(ExpertProfile profile) {
        ExpertProfileRevision revision = profile.getRevision();
        if (revision != null && isOpenReview(revision.getStatus())) {
            List<String> skills = readSkills(revision.getSkillsJson());
            return response(
                    profile.getId(),
                    profile.getUserId(),
                    revision.getDisplayName(),
                    revision.getJobTitle(),
                    revision.getBio(),
                    revision.getPhotoUri(),
                    skills,
                    profile.getStatus(),
                    revision.getReviewNote(),
                    revision.getSubmittedAt(),
                    revision.getReviewedAt(),
                    revision.getReviewedBy(),
                    completion(revision.getPhotoUri(), revision.getBio(), skills, revision.getPortfolio(), revision.getServices()),
                    revisionPortfolio(revision),
                    revisionServices(revision),
                    profile.getStatus() == ExpertProfileStatus.APPROVED,
                    revision.getStatus(),
                    profile.getDisplayName(),
                    profile.getJobTitle(),
                    profile.isAvailable()
            );
        }
        List<String> skills = readSkills(profile.getSkillsJson());
        ExpertProfileStatus updateStatus = revision == null ? null : revision.getStatus();
        return response(
                profile.getId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getJobTitle(),
                profile.getBio(),
                profile.getPhotoUri(),
                skills,
                profile.getStatus(),
                profile.getReviewNote(),
                profile.getSubmittedAt(),
                profile.getReviewedAt(),
                profile.getReviewedBy(),
                completion(profile.getPhotoUri(), profile.getBio(), skills, profile.getPortfolio(), profile.getServices()),
                profilePortfolio(profile),
                profileServices(profile),
                profile.getStatus() == ExpertProfileStatus.APPROVED,
                updateStatus,
                null,
                null,
                profile.isAvailable()
        );
    }

    public ExpertProfileSummaryResponse toPublicSummary(ExpertProfile profile) {
        List<String> skills = readSkills(profile.getSkillsJson());
        return new ExpertProfileSummaryResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getJobTitle(),
                profile.getPhotoUri(),
                profile.getStatus(),
                profile.getSubmittedAt(),
                completion(profile.getPhotoUri(), profile.getBio(), skills, profile.getPortfolio(), profile.getServices()),
                profile.getStatus() == ExpertProfileStatus.APPROVED,
                null,
                profile.isAvailable()
        );
    }

    public ExpertProfileSummaryResponse toAdminSummary(ExpertProfile profile) {
        ExpertProfileRevision revision = profile.getRevision();
        if (revision != null && isOpenReview(revision.getStatus())) {
            List<String> skills = readSkills(revision.getSkillsJson());
            return new ExpertProfileSummaryResponse(
                    profile.getId(),
                    profile.getUserId(),
                    revision.getDisplayName(),
                    revision.getJobTitle(),
                    revision.getPhotoUri(),
                    profile.getStatus(),
                    revision.getSubmittedAt(),
                    completion(revision.getPhotoUri(), revision.getBio(), skills, revision.getPortfolio(), revision.getServices()),
                    profile.getStatus() == ExpertProfileStatus.APPROVED,
                    revision.getStatus(),
                    profile.isAvailable()
            );
        }
        List<String> skills = readSkills(profile.getSkillsJson());
        return new ExpertProfileSummaryResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getDisplayName(),
                profile.getJobTitle(),
                profile.getPhotoUri(),
                profile.getStatus(),
                profile.getSubmittedAt(),
                completion(profile.getPhotoUri(), profile.getBio(), skills, profile.getPortfolio(), profile.getServices()),
                profile.getStatus() == ExpertProfileStatus.APPROVED,
                revision == null ? null : revision.getStatus(),
                profile.isAvailable()
        );
    }

    public String writeSkills(List<String> skills) {
        try {
            List<String> cleaned = skills == null ? List.of() : skills.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize skills", e);
        }
    }

    public List<String> readSkills(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public List<ExpertPortfolioItem> toPortfolioEntities(
            List<com.nmi.platform.skillexpert.model.dto.ExpertProfileUpdateRequest.PortfolioItemRequest> items) {
        List<ExpertPortfolioItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        int order = 0;
        for (var item : items) {
            if (item == null || !StringUtils.hasText(item.mediaUri())) {
                continue;
            }
            ExpertPortfolioItem entity = new ExpertPortfolioItem();
            entity.setMediaUri(item.mediaUri().trim());
            entity.setCaption(blankToNull(item.caption()));
            entity.setSortOrder(order++);
            result.add(entity);
        }
        return result;
    }

    public List<ExpertServiceItem> toServiceEntities(
            List<com.nmi.platform.skillexpert.model.dto.ExpertProfileUpdateRequest.ServiceItemRequest> items) {
        List<ExpertServiceItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        int order = 0;
        for (var item : items) {
            if (item == null || !StringUtils.hasText(item.title())) {
                continue;
            }
            ExpertServiceItem entity = new ExpertServiceItem();
            entity.setTitle(item.title().trim());
            entity.setPrice(blankToNull(item.price()));
            entity.setDescription(blankToNull(item.description()));
            entity.setSortOrder(order++);
            result.add(entity);
        }
        return result;
    }

    public List<ExpertRevisionPortfolioItem> toRevisionPortfolioEntities(
            List<com.nmi.platform.skillexpert.model.dto.ExpertProfileUpdateRequest.PortfolioItemRequest> items) {
        List<ExpertRevisionPortfolioItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        int order = 0;
        for (var item : items) {
            if (item == null || !StringUtils.hasText(item.mediaUri())) {
                continue;
            }
            ExpertRevisionPortfolioItem entity = new ExpertRevisionPortfolioItem();
            entity.setMediaUri(item.mediaUri().trim());
            entity.setCaption(blankToNull(item.caption()));
            entity.setSortOrder(order++);
            result.add(entity);
        }
        return result;
    }

    public List<ExpertRevisionServiceItem> toRevisionServiceEntities(
            List<com.nmi.platform.skillexpert.model.dto.ExpertProfileUpdateRequest.ServiceItemRequest> items) {
        List<ExpertRevisionServiceItem> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        int order = 0;
        for (var item : items) {
            if (item == null || !StringUtils.hasText(item.title())) {
                continue;
            }
            ExpertRevisionServiceItem entity = new ExpertRevisionServiceItem();
            entity.setTitle(item.title().trim());
            entity.setPrice(blankToNull(item.price()));
            entity.setDescription(blankToNull(item.description()));
            entity.setSortOrder(order++);
            result.add(entity);
        }
        return result;
    }

    public boolean isSubmittable(ExpertProfile profile) {
        return isSubmittable(
                profile.getPhotoUri(),
                profile.getBio(),
                profile.getSkillsJson(),
                profile.getPortfolio(),
                profile.getServices());
    }

    public boolean isSubmittable(ExpertProfileRevision revision) {
        return isSubmittable(
                revision.getPhotoUri(),
                revision.getBio(),
                revision.getSkillsJson(),
                revision.getPortfolio(),
                revision.getServices());
    }

    private boolean isOpenReview(ExpertProfileStatus status) {
        return status == ExpertProfileStatus.PENDING || status == ExpertProfileStatus.REJECTED;
    }

    private boolean isSubmittable(String photoUri, String bio, String skillsJson, List<?> portfolio, List<?> services) {
        List<String> skills = readSkills(skillsJson);
        return StringUtils.hasText(photoUri)
                && StringUtils.hasText(bio)
                && !skills.isEmpty()
                && portfolio != null && !portfolio.isEmpty()
                && services != null && !services.isEmpty();
    }

    private int completion(String photoUri, String bio, List<String> skills, List<?> portfolio, List<?> services) {
        int filled = 0;
        int total = 5;
        if (StringUtils.hasText(photoUri)) {
            filled++;
        }
        if (StringUtils.hasText(bio)) {
            filled++;
        }
        if (skills != null && !skills.isEmpty()) {
            filled++;
        }
        if (portfolio != null && !portfolio.isEmpty()) {
            filled++;
        }
        if (services != null && !services.isEmpty()) {
            filled++;
        }
        return (int) Math.round((filled * 100.0) / total);
    }

    private List<ExpertProfileResponse.PortfolioItemResponse> profilePortfolio(ExpertProfile profile) {
        if (profile.getPortfolio() == null) {
            return List.of();
        }
        return profile.getPortfolio().stream()
                .map(item -> new ExpertProfileResponse.PortfolioItemResponse(
                        item.getId(), item.getMediaUri(), item.getCaption(), item.getSortOrder()))
                .toList();
    }

    private List<ExpertProfileResponse.ServiceItemResponse> profileServices(ExpertProfile profile) {
        if (profile.getServices() == null) {
            return List.of();
        }
        return profile.getServices().stream()
                .map(item -> new ExpertProfileResponse.ServiceItemResponse(
                        item.getId(), item.getTitle(), item.getPrice(), item.getDescription(), item.getSortOrder()))
                .toList();
    }

    private List<ExpertProfileResponse.PortfolioItemResponse> revisionPortfolio(ExpertProfileRevision revision) {
        if (revision.getPortfolio() == null) {
            return List.of();
        }
        return revision.getPortfolio().stream()
                .map(item -> new ExpertProfileResponse.PortfolioItemResponse(
                        item.getId(), item.getMediaUri(), item.getCaption(), item.getSortOrder()))
                .toList();
    }

    private List<ExpertProfileResponse.ServiceItemResponse> revisionServices(ExpertProfileRevision revision) {
        if (revision.getServices() == null) {
            return List.of();
        }
        return revision.getServices().stream()
                .map(item -> new ExpertProfileResponse.ServiceItemResponse(
                        item.getId(), item.getTitle(), item.getPrice(), item.getDescription(), item.getSortOrder()))
                .toList();
    }

    private ExpertProfileResponse response(
            Long id,
            String userId,
            String displayName,
            String jobTitle,
            String bio,
            String photoUri,
            List<String> skills,
            ExpertProfileStatus status,
            String reviewNote,
            Instant submittedAt,
            Instant reviewedAt,
            String reviewedBy,
            int completionPercent,
            List<ExpertProfileResponse.PortfolioItemResponse> portfolio,
            List<ExpertProfileResponse.ServiceItemResponse> services,
            boolean liveListing,
            ExpertProfileStatus updateStatus,
            String liveDisplayName,
            String liveJobTitle,
            boolean available) {
        return new ExpertProfileResponse(
                id,
                userId,
                displayName,
                jobTitle,
                bio,
                photoUri,
                skills,
                status,
                reviewNote,
                submittedAt,
                reviewedAt,
                reviewedBy,
                completionPercent,
                portfolio,
                services,
                liveListing,
                updateStatus,
                liveDisplayName,
                liveJobTitle,
                available
        );
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
