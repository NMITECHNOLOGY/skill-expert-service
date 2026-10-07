/**
 * One star rating left after a finished booking.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.model.entity;

import java.time.Instant;

import com.nmi.platform.skillexpert.model.enums.ReviewDirection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "booking_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_reviews_booking_direction",
                columnNames = {"booking_id", "direction"}))
@Getter
@Setter
public class BookingReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private ExpertBooking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 32)
    private ReviewDirection direction;

    @Column(name = "stars", nullable = false)
    private int stars;

    @Column(name = "comment", length = 500)
    private String comment;

    @Column(name = "author_user_id", nullable = false, length = 64)
    private String authorUserId;

    @Column(name = "author_name", nullable = false, length = 200)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
