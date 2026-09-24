package com.nmi.platform.skillexpert.model.entity;

import java.time.Instant;

import com.nmi.platform.skillexpert.model.enums.BookingStatus;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expert_bookings")
@Getter
@Setter
public class ExpertBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private ExpertProfile profile;

    @Column(name = "customer_user_id", nullable = false, length = 64)
    private String customerUserId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "service_title", nullable = false, length = 200)
    private String serviceTitle;

    @Column(name = "price", length = 64)
    private String price;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = BookingStatus.REQUESTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
