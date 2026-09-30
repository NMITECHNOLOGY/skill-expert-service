/**
 * Stored booking reviews.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nmi.platform.skillexpert.model.entity.BookingReview;
import com.nmi.platform.skillexpert.model.enums.ReviewDirection;

public interface BookingReviewRepository extends JpaRepository<BookingReview, Long> {

    Optional<BookingReview> findByBooking_IdAndDirection(Long bookingId, ReviewDirection direction);

    Page<BookingReview> findByBooking_Profile_IdAndDirection(
            Long profileId,
            ReviewDirection direction,
            Pageable pageable);

    @Query("""
            select b.profile.id, r.stars, count(r)
            from BookingReview r
            join r.booking b
            where b.profile.id in :profileIds
              and r.direction = com.nmi.platform.skillexpert.model.enums.ReviewDirection.FINDER_TO_EXPERT
            group by b.profile.id, r.stars
            """)
    List<Object[]> countFinderStars(@Param("profileIds") Collection<Long> profileIds);

    @Query("""
            select r from BookingReview r
            join fetch r.booking b
            join fetch b.profile
            where replace(lower(b.customerUserId), '-', '') in :keys
               or replace(lower(b.profile.userId), '-', '') in :keys
            order by r.createdAt desc
            """)
    List<BookingReview> findForParties(@Param("keys") Collection<String> keys);
}
