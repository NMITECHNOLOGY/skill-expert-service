/**
 * Customer ratings for a finished booking. Only the customer can rate the expert.
 *
 * <p>
 * Author: Viraj Sachin
 * Created: 2026-09-28
 * <p>
 * Copyright (c) 2026 NMI Infra Pvt Ltd
 */
package com.nmi.platform.skillexpert.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nmi.platform.skillexpert.model.dto.PartyReviewResponse;
import com.nmi.platform.skillexpert.model.dto.PublicReviewResponse;
import com.nmi.platform.skillexpert.model.dto.ReviewPageResponse;
import com.nmi.platform.skillexpert.model.dto.ReviewSummaryResponse;
import com.nmi.platform.skillexpert.model.dto.SubmitBookingReviewRequest;
import com.nmi.platform.skillexpert.model.entity.BookingReview;
import com.nmi.platform.skillexpert.model.entity.ExpertBooking;
import com.nmi.platform.skillexpert.model.entity.ExpertProfile;
import com.nmi.platform.skillexpert.model.enums.BookingStatus;
import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;
import com.nmi.platform.skillexpert.model.enums.ReviewDirection;
import com.nmi.platform.skillexpert.repository.BookingReviewRepository;
import com.nmi.platform.skillexpert.repository.ExpertBookingRepository;
import com.nmi.platform.skillexpert.repository.ExpertProfileRepository;
import com.nmi.platform.skillexpert.web.ConflictException;
import com.nmi.platform.skillexpert.web.NotFoundException;

@Service
public class BookingReviewService {

    private final BookingReviewRepository reviews;
    private final ExpertBookingRepository bookings;
    private final ExpertProfileRepository profiles;

    public BookingReviewService(
            BookingReviewRepository reviews,
            ExpertBookingRepository bookings,
            ExpertProfileRepository profiles) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.profiles = profiles;
    }

    @Transactional
    public PartyReviewResponse submit(String userId, Collection<String> identityKeys, Long bookingId, SubmitBookingReviewRequest request) {
        ExpertBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        ReviewDirection direction = directionFor(booking, identityKeys);
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ConflictException("You can rate this after the job is done.");
        }
        if (reviews.findByBooking_IdAndDirection(bookingId, direction).isPresent()) {
            throw new ConflictException("You already rated this job.");
        }
        BookingReview review = new BookingReview();
        review.setBooking(booking);
        review.setDirection(direction);
        review.setStars(request.stars());
        review.setComment(blankToNull(request.comment()));
        review.setAuthorUserId(userId);
        review.setAuthorName(authorName(booking));
        try {
            return toParty(reviews.saveAndFlush(review));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("You already rated this job.");
        }
    }

    @Transactional(readOnly = true)
    public List<PartyReviewResponse> mine(Collection<String> identityKeys) {
        List<String> keys = identityKeys.stream()
                .map(BookingReviewService::userKey)
                .filter(key -> !key.isEmpty())
                .distinct()
                .toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        return reviews.findForParties(keys).stream()
                .filter(review -> review.getDirection() == ReviewDirection.FINDER_TO_EXPERT)
                .map(this::toParty)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse publicReviews(Long profileId, Pageable pageable) {
        ExpertProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() != ExpertProfileStatus.APPROVED) {
            throw new NotFoundException("Expert profile not found");
        }
        Page<BookingReview> page = reviews.findByBooking_Profile_IdAndDirection(
                profileId,
                ReviewDirection.FINDER_TO_EXPERT,
                pageable);
        List<PublicReviewResponse> content = page.getContent().stream().map(this::toPublic).toList();
        return new ReviewPageResponse(
                summary(profileId),
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.isLast());
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long profileId) {
        if (profileId == null) {
            return ReviewSummaryResponse.empty();
        }
        return summaries(List.of(profileId)).getOrDefault(profileId, ReviewSummaryResponse.empty());
    }

    @Transactional(readOnly = true)
    public Map<Long, ReviewSummaryResponse> summaries(Collection<Long> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> stars = new HashMap<>();
        for (Object[] row : reviews.countFinderStars(profileIds)) {
            long profileId = number(row[0]);
            int star = (int) number(row[1]);
            long count = number(row[2]);
            if (star >= 1 && star <= 5) {
                stars.computeIfAbsent(profileId, id -> new long[6])[star] = count;
            }
        }
        Map<Long, long[]> jobs = new HashMap<>();
        for (Object[] row : bookings.countByProfileAndStatus(profileIds)) {
            long profileId = number(row[0]);
            BookingStatus status = statusOf(row[1]);
            long count = number(row[2]);
            if (status != null) {
                jobs.computeIfAbsent(profileId, id -> new long[BookingStatus.values().length])[status.ordinal()] = count;
            }
        }
        Map<Long, ReviewSummaryResponse> result = new HashMap<>();
        for (Long profileId : profileIds) {
            if (profileId == null) {
                continue;
            }
            long[] star = stars.getOrDefault(profileId, new long[6]);
            long[] job = jobs.getOrDefault(profileId, new long[BookingStatus.values().length]);
            long one = star[1];
            long two = star[2];
            long three = star[3];
            long four = star[4];
            long five = star[5];
            long count = one + two + three + four + five;
            int reviewCount = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
            result.put(profileId, new ReviewSummaryResponse(
                    ReviewScore.average(one, two, three, four, five),
                    reviewCount,
                    five,
                    four,
                    three,
                    two,
                    one,
                    job[BookingStatus.COMPLETED.ordinal()],
                    ReviewScore.responseRate(
                            job[BookingStatus.REQUESTED.ordinal()],
                            job[BookingStatus.CONFIRMED.ordinal()],
                            job[BookingStatus.DECLINED.ordinal()],
                            job[BookingStatus.COMPLETED.ordinal()])));
        }
        return result;
    }

    private ReviewDirection directionFor(ExpertBooking booking, Collection<String> identityKeys) {
        boolean finder = matches(booking.getCustomerUserId(), identityKeys);
        boolean expert = matches(booking.getProfile().getUserId(), identityKeys);
        if (!finder || expert) {
            if (!finder && expert) {
                throw new ConflictException("Only the customer can rate this job.");
            }
            throw new NotFoundException("Booking not found");
        }
        return ReviewDirection.FINDER_TO_EXPERT;
    }

    private static boolean matches(String userId, Collection<String> keys) {
        if (keys == null) {
            return false;
        }
        for (String key : keys) {
            if (sameUser(userId, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameUser(String left, String right) {
        String a = userKey(left);
        String b = userKey(right);
        return !a.isEmpty() && a.equals(b);
    }

    private static String userKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace("-", "");
    }

    private static String authorName(ExpertBooking booking) {
        String preferred = booking.getCustomerName();
        String text = StringUtils.hasText(preferred) ? preferred.trim() : "Customer";
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static long number(Object value) {
        if (value instanceof Number numeric) {
            return numeric.longValue();
        }
        return 0;
    }

    private static BookingStatus statusOf(Object value) {
        if (value instanceof BookingStatus status) {
            return status;
        }
        if (value == null) {
            return null;
        }
        try {
            return BookingStatus.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PartyReviewResponse toParty(BookingReview review) {
        return new PartyReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getDirection(),
                review.getStars(),
                review.getComment(),
                review.getAuthorName(),
                review.getCreatedAt());
    }

    private PublicReviewResponse toPublic(BookingReview review) {
        return new PublicReviewResponse(
                review.getId(),
                review.getAuthorName(),
                review.getStars(),
                review.getComment(),
                review.getBooking().getServiceTitle(),
                review.getCreatedAt());
    }
}
