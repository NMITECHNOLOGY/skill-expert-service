package com.nmi.platform.skillexpert.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nmi.platform.skillexpert.model.dto.BookingResponse;
import com.nmi.platform.skillexpert.model.dto.CreateBookingRequest;
import com.nmi.platform.skillexpert.model.entity.ExpertBooking;
import com.nmi.platform.skillexpert.model.entity.ExpertProfile;
import com.nmi.platform.skillexpert.model.enums.BookingStatus;
import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;
import com.nmi.platform.skillexpert.repository.ExpertBookingRepository;
import com.nmi.platform.skillexpert.repository.ExpertProfileRepository;
import com.nmi.platform.skillexpert.web.BadRequestException;
import com.nmi.platform.skillexpert.web.ConflictException;
import com.nmi.platform.skillexpert.web.NotFoundException;

@Service
public class ExpertBookingService {

    private final ExpertBookingRepository bookings;
    private final ExpertProfileRepository profiles;

    public ExpertBookingService(ExpertBookingRepository bookings, ExpertProfileRepository profiles) {
        this.bookings = bookings;
        this.profiles = profiles;
    }

    @Transactional
    public BookingResponse create(String customerUserId, String customerName, Long profileId, CreateBookingRequest request) {
        ExpertProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() != ExpertProfileStatus.APPROVED) {
            throw new NotFoundException("Expert profile not found");
        }
        if (profile.getUserId().equals(customerUserId)) {
            throw new BadRequestException("You cannot request yourself.");
        }
        if (!profile.isAvailable()) {
            throw new ConflictException("This expert is not free right now.");
        }
        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new BadRequestException("Pick a future time.");
        }
        ExpertBooking booking = new ExpertBooking();
        booking.setProfile(profile);
        booking.setCustomerUserId(customerUserId);
        booking.setCustomerName(trimTo(customerName, 200, "Customer"));
        booking.setServiceTitle(request.serviceTitle().trim());
        booking.setPrice(blankToNull(request.price()));
        booking.setAddress(blankToNull(request.address()));
        booking.setNote(blankToNull(request.note()));
        booking.setScheduledAt(request.scheduledAt());
        booking.setStatus(BookingStatus.REQUESTED);
        return toResponse(bookings.save(booking));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForCustomer(String customerUserId) {
        return bookings.findByCustomerUserIdOrderByScheduledAtDesc(customerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForExpert(String expertUserId) {
        return bookings.findByProfile_UserIdOrderByScheduledAtAsc(expertUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BookingResponse accept(String expertUserId, Long bookingId) {
        return transition(loadForExpert(expertUserId, bookingId), BookingStatus.REQUESTED, BookingStatus.CONFIRMED);
    }

    @Transactional
    public BookingResponse decline(String expertUserId, Long bookingId) {
        return transition(loadForExpert(expertUserId, bookingId), BookingStatus.REQUESTED, BookingStatus.DECLINED);
    }

    @Transactional
    public BookingResponse complete(String expertUserId, Long bookingId) {
        return transition(loadForExpert(expertUserId, bookingId), BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    }

    @Transactional
    public BookingResponse cancel(String customerUserId, Long bookingId) {
        ExpertBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getCustomerUserId().equals(customerUserId)) {
            throw new NotFoundException("Booking not found");
        }
        if (booking.getStatus() != BookingStatus.REQUESTED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("This request can no longer be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return toResponse(bookings.save(booking));
    }

    private BookingResponse transition(ExpertBooking booking, BookingStatus from, BookingStatus to) {
        if (booking.getStatus() != from) {
            throw new ConflictException("This request is no longer waiting for that step.");
        }
        booking.setStatus(to);
        return toResponse(bookings.save(booking));
    }

    private ExpertBooking loadForExpert(String expertUserId, Long bookingId) {
        ExpertBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getProfile().getUserId().equals(expertUserId)) {
            throw new NotFoundException("Booking not found");
        }
        return booking;
    }

    private BookingResponse toResponse(ExpertBooking booking) {
        ExpertProfile profile = booking.getProfile();
        String name = StringUtils.hasText(profile.getDisplayName()) ? profile.getDisplayName() : "Expert";
        return new BookingResponse(
                booking.getId(),
                profile.getId(),
                name,
                profile.getJobTitle(),
                booking.getCustomerUserId(),
                booking.getCustomerName(),
                booking.getServiceTitle(),
                booking.getPrice(),
                booking.getAddress(),
                booking.getNote(),
                booking.getScheduledAt(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String trimTo(String value, int max, String fallback) {
        String text = StringUtils.hasText(value) ? value.trim() : fallback;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
