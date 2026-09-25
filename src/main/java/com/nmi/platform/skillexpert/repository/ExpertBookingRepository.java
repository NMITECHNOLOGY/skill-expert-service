package com.nmi.platform.skillexpert.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nmi.platform.skillexpert.model.entity.ExpertBooking;
import com.nmi.platform.skillexpert.model.enums.BookingStatus;

public interface ExpertBookingRepository extends JpaRepository<ExpertBooking, Long> {

    Optional<ExpertBooking> findByPaymentReference(String paymentReference);

    List<ExpertBooking> findByCustomerUserIdOrderByScheduledAtDesc(String customerUserId);

    List<ExpertBooking> findByProfile_UserIdOrderByScheduledAtAsc(String expertUserId);

    @Query("""
            select b.scheduledAt from ExpertBooking b
            where b.profile.id = :profileId
              and b.status in :statuses
              and b.scheduledAt >= :from
              and b.scheduledAt < :until
            order by b.scheduledAt
            """)
    List<Instant> findTakenTimes(
            @Param("profileId") Long profileId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("from") Instant from,
            @Param("until") Instant until);

    @Query("""
            select b.scheduledAt from ExpertBooking b
            where b.customerUserId = :customerUserId
              and b.status in :statuses
              and b.scheduledAt >= :from
              and b.scheduledAt < :until
            order by b.scheduledAt
            """)
    List<Instant> findCustomerTakenTimes(
            @Param("customerUserId") String customerUserId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("from") Instant from,
            @Param("until") Instant until);

    @Query("""
            select count(b) from ExpertBooking b
            where b.profile.userId = :expertUserId
              and b.status in :statuses
              and (:exceptId is null or b.id <> :exceptId)
              and b.scheduledAt > :windowStart
              and b.scheduledAt < :windowEnd
            """)
    long countOwnedExpertOverlap(
            @Param("expertUserId") String expertUserId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("exceptId") Long exceptId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    @Query("""
            select count(b) from ExpertBooking b
            where b.profile.id = :profileId
              and b.status in :statuses
              and (:exceptId is null or b.id <> :exceptId)
              and b.scheduledAt > :windowStart
              and b.scheduledAt < :windowEnd
            """)
    long countExpertOverlap(
            @Param("profileId") Long profileId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("exceptId") Long exceptId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    @Query("""
            select count(b) from ExpertBooking b
            where b.customerUserId = :customerUserId
              and b.status in :statuses
              and (:exceptId is null or b.id <> :exceptId)
              and b.scheduledAt > :windowStart
              and b.scheduledAt < :windowEnd
            """)
    long countCustomerOverlap(
            @Param("customerUserId") String customerUserId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("exceptId") Long exceptId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
