package com.nmi.platform.skillexpert.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * In-progress edit of an already approved profile. The live row stays public
 * until an admin approves this revision.
 */
@Entity
@Table(name = "expert_profile_revisions")
@Getter
@Setter
public class ExpertProfileRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private ExpertProfile profile;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "photo_uri", columnDefinition = "TEXT")
    private String photoUri;

    @Column(name = "skills_json", columnDefinition = "TEXT")
    private String skillsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExpertProfileStatus status = ExpertProfileStatus.DRAFT;

    @Column(name = "review_note", length = 2000)
    private String reviewNote;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ExpertRevisionPortfolioItem> portfolio = new ArrayList<>();

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ExpertRevisionServiceItem> services = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ExpertProfileStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replacePortfolio(List<ExpertRevisionPortfolioItem> items) {
        portfolio.clear();
        if (items != null) {
            items.forEach(item -> {
                item.setRevision(this);
                portfolio.add(item);
            });
        }
    }

    public void replaceServices(List<ExpertRevisionServiceItem> items) {
        services.clear();
        if (items != null) {
            items.forEach(item -> {
                item.setRevision(this);
                services.add(item);
            });
        }
    }
}
