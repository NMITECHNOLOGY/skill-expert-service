package com.nmi.platform.skillexpert.service;

import java.time.Instant;
import java.util.EnumSet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nmi.platform.skillexpert.model.dto.ExpertProfileResponse;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileSummaryResponse;
import com.nmi.platform.skillexpert.model.dto.ExpertProfileUpdateRequest;
import com.nmi.platform.skillexpert.model.dto.ExpertReviewRequest;
import com.nmi.platform.skillexpert.model.entity.ExpertPortfolioItem;
import com.nmi.platform.skillexpert.model.entity.ExpertProfile;
import com.nmi.platform.skillexpert.model.entity.ExpertProfileRevision;
import com.nmi.platform.skillexpert.model.entity.ExpertRevisionPortfolioItem;
import com.nmi.platform.skillexpert.model.entity.ExpertRevisionServiceItem;
import com.nmi.platform.skillexpert.model.entity.ExpertServiceItem;
import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;
import com.nmi.platform.skillexpert.repository.ExpertProfileRepository;
import com.nmi.platform.skillexpert.web.BadRequestException;
import com.nmi.platform.skillexpert.web.ConflictException;
import com.nmi.platform.skillexpert.web.NotFoundException;

@Service
public class ExpertProfileService {

    private static final String INCOMPLETE =
            "Complete photo, bio, skills, at least one portfolio item, and one service before submitting.";

    private final ExpertProfileRepository repository;
    private final ExpertProfileMapper mapper;

    public ExpertProfileService(ExpertProfileRepository repository, ExpertProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ExpertProfileResponse getMine(String userId) {
        return repository.findByUserId(userId)
                .map(mapper::toMine)
                .orElseGet(() -> mapper.emptyMine(userId));
    }

    @Transactional
    public ExpertProfileResponse saveDraft(String userId, ExpertProfileUpdateRequest request) {
        ExpertProfile profile = loadOrCreate(userId);
        if (profile.getStatus() == ExpertProfileStatus.APPROVED) {
            return mapper.toMine(saveApprovedEdit(profile, request));
        }
        if (profile.getStatus() == ExpertProfileStatus.PENDING) {
            throw new ConflictException("Profile is under review and cannot be edited");
        }
        applyProfileUpdate(profile, request);
        if (profile.getStatus() == ExpertProfileStatus.NOT_STARTED
                || profile.getStatus() == ExpertProfileStatus.REJECTED) {
            profile.setStatus(ExpertProfileStatus.DRAFT);
        }
        profile.setReviewNote(null);
        return mapper.toMine(repository.save(profile));
    }

    @Transactional
    public ExpertProfileResponse submit(String userId) {
        ExpertProfile profile = repository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Save a draft before submitting."));
        if (profile.getStatus() == ExpertProfileStatus.APPROVED) {
            return mapper.toMine(submitApprovedEdit(profile));
        }
        if (profile.getStatus() == ExpertProfileStatus.PENDING) {
            throw new ConflictException("Profile is under review and cannot be edited");
        }
        if (!mapper.isSubmittable(profile)) {
            throw new BadRequestException(INCOMPLETE);
        }
        profile.setStatus(ExpertProfileStatus.PENDING);
        profile.setSubmittedAt(Instant.now());
        profile.setReviewedAt(null);
        profile.setReviewedBy(null);
        profile.setReviewNote(null);
        return mapper.toMine(repository.save(profile));
    }

    @Transactional(readOnly = true)
    public Page<ExpertProfileSummaryResponse> listApproved(Pageable pageable) {
        return repository.findByStatus(ExpertProfileStatus.APPROVED, pageable).map(mapper::toPublicSummary);
    }

    @Transactional(readOnly = true)
    public ExpertProfileResponse getApproved(Long id) {
        ExpertProfile profile = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() != ExpertProfileStatus.APPROVED) {
            throw new NotFoundException("Expert profile not found");
        }
        return mapper.toPublic(profile);
    }

    @Transactional(readOnly = true)
    public Page<ExpertProfileSummaryResponse> adminList(String status, String search, Pageable pageable) {
        ExpertProfileStatus parsed = parseStatus(status);
        String term = StringUtils.hasText(search) ? search.trim() : null;
        return repository.searchAdmin(
                EnumSet.of(ExpertProfileStatus.PENDING, ExpertProfileStatus.APPROVED, ExpertProfileStatus.REJECTED),
                EnumSet.of(ExpertProfileStatus.PENDING, ExpertProfileStatus.REJECTED),
                parsed,
                term,
                pageable
        ).map(mapper::toAdminSummary);
    }

    @Transactional(readOnly = true)
    public ExpertProfileResponse adminGet(Long id) {
        return mapper.toAdmin(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expert profile not found")));
    }

    @Transactional
    public ExpertProfileResponse approve(Long id, ExpertReviewRequest request, String reviewer) {
        ExpertProfile profile = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() == ExpertProfileStatus.PENDING) {
            if (!mapper.isSubmittable(profile)) {
                throw new BadRequestException(INCOMPLETE);
            }
            profile.setStatus(ExpertProfileStatus.APPROVED);
            profile.setReviewedAt(Instant.now());
            profile.setReviewedBy(reviewer);
            profile.setReviewNote(request != null ? request.note() : null);
            return mapper.toAdmin(repository.save(profile));
        }
        ExpertProfileRevision revision = profile.getRevision();
        if (revision != null && revision.getStatus() == ExpertProfileStatus.PENDING) {
            if (!mapper.isSubmittable(revision)) {
                throw new BadRequestException(INCOMPLETE);
            }
            publishRevision(profile, request, reviewer);
            return mapper.toAdmin(repository.save(profile));
        }
        throw new ConflictException("Only a pending profile or a pending update can be approved");
    }

    @Transactional
    public ExpertProfileResponse reject(Long id, ExpertReviewRequest.RejectRequest request, String reviewer) {
        ExpertProfile profile = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() == ExpertProfileStatus.PENDING) {
            profile.setStatus(ExpertProfileStatus.REJECTED);
            profile.setReviewedAt(Instant.now());
            profile.setReviewedBy(reviewer);
            profile.setReviewNote(request.note());
            return mapper.toAdmin(repository.save(profile));
        }
        ExpertProfileRevision revision = profile.getRevision();
        if (revision != null && revision.getStatus() == ExpertProfileStatus.PENDING) {
            revision.setStatus(ExpertProfileStatus.REJECTED);
            revision.setReviewedAt(Instant.now());
            revision.setReviewedBy(reviewer);
            revision.setReviewNote(request.note());
            return mapper.toAdmin(repository.save(profile));
        }
        throw new ConflictException("Only a pending profile or a pending update can be rejected");
    }

    private ExpertProfile saveApprovedEdit(ExpertProfile profile, ExpertProfileUpdateRequest request) {
        ExpertProfileRevision revision = profile.getRevision();
        if (revision != null && revision.getStatus() == ExpertProfileStatus.PENDING) {
            throw new ConflictException("Profile update is under review and cannot be edited");
        }
        if (revision == null) {
            revision = revisionFromLive(profile);
            profile.setRevision(revision);
        }
        applyRevisionUpdate(revision, request);
        if (revision.getStatus() != ExpertProfileStatus.DRAFT) {
            revision.setStatus(ExpertProfileStatus.DRAFT);
            revision.setReviewNote(null);
            revision.setReviewedAt(null);
            revision.setReviewedBy(null);
        }
        return repository.save(profile);
    }

    private ExpertProfile submitApprovedEdit(ExpertProfile profile) {
        ExpertProfileRevision revision = profile.getRevision();
        if (revision == null || revision.getStatus() == ExpertProfileStatus.PENDING) {
            throw new BadRequestException("Save a draft update before submitting.");
        }
        if (!mapper.isSubmittable(revision)) {
            throw new BadRequestException(INCOMPLETE);
        }
        revision.setStatus(ExpertProfileStatus.PENDING);
        revision.setSubmittedAt(Instant.now());
        revision.setReviewedAt(null);
        revision.setReviewedBy(null);
        revision.setReviewNote(null);
        return repository.save(profile);
    }

    private void publishRevision(ExpertProfile profile, ExpertReviewRequest request, String reviewer) {
        ExpertProfileRevision revision = profile.getRevision();
        profile.setDisplayName(revision.getDisplayName());
        profile.setJobTitle(revision.getJobTitle());
        profile.setBio(revision.getBio());
        profile.setPhotoUri(revision.getPhotoUri());
        profile.setSkillsJson(revision.getSkillsJson());
        profile.replacePortfolio(revision.getPortfolio().stream().map(item -> {
            ExpertPortfolioItem copy = new ExpertPortfolioItem();
            copy.setMediaUri(item.getMediaUri());
            copy.setCaption(item.getCaption());
            copy.setSortOrder(item.getSortOrder());
            return copy;
        }).toList());
        profile.replaceServices(revision.getServices().stream().map(item -> {
            ExpertServiceItem copy = new ExpertServiceItem();
            copy.setTitle(item.getTitle());
            copy.setPrice(item.getPrice());
            copy.setDescription(item.getDescription());
            copy.setSortOrder(item.getSortOrder());
            return copy;
        }).toList());
        profile.setStatus(ExpertProfileStatus.APPROVED);
        profile.setReviewedAt(Instant.now());
        profile.setReviewedBy(reviewer);
        profile.setReviewNote(request != null ? request.note() : null);
        profile.setSubmittedAt(revision.getSubmittedAt());
        profile.setRevision(null);
    }

    private ExpertProfileRevision revisionFromLive(ExpertProfile profile) {
        ExpertProfileRevision revision = new ExpertProfileRevision();
        revision.setProfile(profile);
        revision.setDisplayName(profile.getDisplayName());
        revision.setJobTitle(profile.getJobTitle());
        revision.setBio(profile.getBio());
        revision.setPhotoUri(profile.getPhotoUri());
        revision.setSkillsJson(profile.getSkillsJson() == null ? "[]" : profile.getSkillsJson());
        revision.setStatus(ExpertProfileStatus.DRAFT);
        revision.replacePortfolio(profile.getPortfolio().stream().map(item -> {
            ExpertRevisionPortfolioItem copy = new ExpertRevisionPortfolioItem();
            copy.setMediaUri(item.getMediaUri());
            copy.setCaption(item.getCaption());
            copy.setSortOrder(item.getSortOrder());
            return copy;
        }).toList());
        revision.replaceServices(profile.getServices().stream().map(item -> {
            ExpertRevisionServiceItem copy = new ExpertRevisionServiceItem();
            copy.setTitle(item.getTitle());
            copy.setPrice(item.getPrice());
            copy.setDescription(item.getDescription());
            copy.setSortOrder(item.getSortOrder());
            return copy;
        }).toList());
        return revision;
    }

    private ExpertProfile loadOrCreate(String userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            ExpertProfile created = new ExpertProfile();
            created.setUserId(userId);
            created.setStatus(ExpertProfileStatus.NOT_STARTED);
            created.setSkillsJson("[]");
            return created;
        });
    }

    private void applyProfileUpdate(ExpertProfile profile, ExpertProfileUpdateRequest request) {
        if (request.displayName() != null) {
            profile.setDisplayName(blankToNull(request.displayName()));
        }
        if (request.jobTitle() != null) {
            profile.setJobTitle(blankToNull(request.jobTitle()));
        }
        if (request.bio() != null) {
            profile.setBio(blankToNull(request.bio()));
        }
        if (request.photoUri() != null) {
            profile.setPhotoUri(blankToNull(request.photoUri()));
        }
        if (request.skills() != null) {
            profile.setSkillsJson(mapper.writeSkills(request.skills()));
        }
        if (request.portfolio() != null) {
            profile.replacePortfolio(mapper.toPortfolioEntities(request.portfolio()));
        }
        if (request.services() != null) {
            profile.replaceServices(mapper.toServiceEntities(request.services()));
        }
    }

    private void applyRevisionUpdate(ExpertProfileRevision revision, ExpertProfileUpdateRequest request) {
        if (request.displayName() != null) {
            revision.setDisplayName(blankToNull(request.displayName()));
        }
        if (request.jobTitle() != null) {
            revision.setJobTitle(blankToNull(request.jobTitle()));
        }
        if (request.bio() != null) {
            revision.setBio(blankToNull(request.bio()));
        }
        if (request.photoUri() != null) {
            revision.setPhotoUri(blankToNull(request.photoUri()));
        }
        if (request.skills() != null) {
            revision.setSkillsJson(mapper.writeSkills(request.skills()));
        }
        if (request.portfolio() != null) {
            revision.replacePortfolio(mapper.toRevisionPortfolioEntities(request.portfolio()));
        }
        if (request.services() != null) {
            revision.replaceServices(mapper.toRevisionServiceEntities(request.services()));
        }
    }

    private ExpertProfileStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ExpertProfileStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown status: " + status);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
