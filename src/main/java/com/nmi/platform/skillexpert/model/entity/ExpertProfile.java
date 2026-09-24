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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expert_profiles")
@Getter
@Setter
public class ExpertProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "photo_uri", columnDefinition = "TEXT")
    private String photoUri;

    /** JSON array of skill strings. */
    @Column(name = "skills_json", columnDefinition = "TEXT")
    private String skillsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExpertProfileStatus status = ExpertProfileStatus.NOT_STARTED;

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

    /** Expert can turn this off without sending the profile for review again. */
    @Column(name = "available", nullable = false)
    private boolean available = true;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ExpertPortfolioItem> portfolio = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ExpertServiceItem> services = new ArrayList<>();

    /** Present only while an approved expert has an unpublished edit. */
    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private ExpertProfileRevision revision;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ExpertProfileStatus.NOT_STARTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replacePortfolio(List<ExpertPortfolioItem> items) {
        portfolio.clear();
        if (items != null) {
            items.forEach(item -> {
                item.setProfile(this);
                portfolio.add(item);
            });
        }
    }

    public void replaceServices(List<ExpertServiceItem> items) {
        services.clear();
        if (items != null) {
            items.forEach(item -> {
                item.setProfile(this);
                services.add(item);
            });
        }
    }
}
