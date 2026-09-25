package com.nmi.platform.skillexpert.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.nmi.platform.skillexpert.model.dto.BookingResponse;
import com.nmi.platform.skillexpert.model.dto.CreateBookingRequest;
import com.nmi.platform.skillexpert.model.dto.RecordBookingPaymentRequest;
import com.nmi.platform.skillexpert.model.entity.ExpertBooking;
import com.nmi.platform.skillexpert.model.entity.ExpertProfile;
import com.nmi.platform.skillexpert.model.entity.ExpertServiceItem;
import com.nmi.platform.skillexpert.model.enums.BookingStatus;
import com.nmi.platform.skillexpert.model.enums.ExpertProfileStatus;
import com.nmi.platform.skillexpert.repository.ExpertBookingRepository;
import com.nmi.platform.skillexpert.repository.ExpertProfileRepository;
import com.nmi.platform.skillexpert.web.BadRequestException;
import com.nmi.platform.skillexpert.web.ConflictException;
import com.nmi.platform.skillexpert.web.NotFoundException;

@Service
public class ExpertBookingService {

    /** One visit holds the clock hour around its start. The next hour is a new visit. */
    private static final Duration VISIT = Duration.ofHours(1);
    private static final Duration HORIZON = Duration.ofDays(30);
    private static final int MIN_ADDRESS_LENGTH = 5;
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Set<BookingStatus> HOLDING_THE_SLOT = EnumSet.of(
            BookingStatus.REQUESTED,
            BookingStatus.CONFIRMED);

    private final ExpertBookingRepository bookings;
    private final ExpertProfileRepository profiles;
    private final String merchantId;

    public ExpertBookingService(
            ExpertBookingRepository bookings,
            ExpertProfileRepository profiles,
            @Value("${nmi.skill-expert.merchant-id}") String merchantId) {
        this.bookings = bookings;
        this.profiles = profiles;
        this.merchantId = merchantId;
    }

    @Transactional
    public BookingResponse create(
            String customerUserId,
            Collection<String> identityKeys,
            String customerName,
            Long profileId,
            CreateBookingRequest request) {
        ExpertProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() != ExpertProfileStatus.APPROVED) {
            throw new NotFoundException("Expert profile not found");
        }
        if (sameUser(profile.getUserId(), customerUserId) || matchesAny(profile.getUserId(), identityKeys)) {
            throw new BadRequestException("You cannot request yourself.");
        }
        if (!profile.isAvailable()) {
            throw new ConflictException("This expert is not free right now.");
        }
        Instant when = request.scheduledAt();
        if (!when.isAfter(Instant.now())) {
            throw new BadRequestException("Pick a future time.");
        }
        if (when.isAfter(Instant.now().plus(HORIZON))) {
            throw new BadRequestException("Pick a time within the next 30 days.");
        }
        String address = blankToNull(request.address());
        if (address == null || address.length() < MIN_ADDRESS_LENGTH) {
            throw new BadRequestException("Add the address where they should come.");
        }
        String serviceTitle = request.serviceTitle().trim();
        assertKnownService(profile, serviceTitle, request.price());
        assertCreateSlot(profile, customerUserId, when);
        ExpertBooking booking = new ExpertBooking();
        booking.setProfile(profile);
        booking.setCustomerUserId(customerUserId);
        booking.setCustomerName(trimTo(preferredCustomerName(request.customerName(), customerName), 200, "Customer"));
        booking.setServiceTitle(serviceTitle);
        booking.setPrice(blankToNull(request.price()));
        booking.setAddress(address);
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

    @Transactional(readOnly = true)
    public List<Instant> takenTimes(Long profileId) {
        ExpertProfile profile = profiles.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Expert profile not found"));
        if (profile.getStatus() != ExpertProfileStatus.APPROVED) {
            throw new NotFoundException("Expert profile not found");
        }
        Instant from = Instant.now();
        Instant until = from.plus(HORIZON);
        List<Instant> times = new ArrayList<>(bookings.findTakenTimes(profileId, HOLDING_THE_SLOT, from, until));
        times.addAll(bookings.findCustomerTakenTimes(profile.getUserId(), HOLDING_THE_SLOT, from, until));
        return times.stream().distinct().sorted().toList();
    }

    @Transactional
    public BookingResponse accept(String expertUserId, Long bookingId) {
        ExpertBooking booking = loadForExpert(expertUserId, bookingId);
        if (sameUser(booking.getCustomerUserId(), booking.getProfile().getUserId())) {
            throw new BadRequestException("You cannot request yourself.");
        }
        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw new ConflictException("This request is no longer waiting for that step.");
        }
        assertAcceptSlot(booking);
        return transition(booking, BookingStatus.REQUESTED, BookingStatus.CONFIRMED);
    }

    @Transactional
    public BookingResponse decline(String expertUserId, Long bookingId) {
        return transition(loadForExpert(expertUserId, bookingId), BookingStatus.REQUESTED, BookingStatus.DECLINED);
    }

    @Transactional
    public BookingResponse complete(String expertUserId, Long bookingId) {
        ExpertBooking booking = loadForExpert(expertUserId, bookingId);
        if (sameUser(booking.getCustomerUserId(), booking.getProfile().getUserId())) {
            throw new BadRequestException("You cannot request yourself.");
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED && requiresPayment(booking.getPrice())
                && !StringUtils.hasText(booking.getPaymentReference())) {
            throw new ConflictException("The customer has not paid yet.");
        }
        return transition(booking, BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    }

    @Transactional
    public BookingResponse cancel(String customerUserId, Long bookingId) {
        ExpertBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getCustomerUserId().equals(customerUserId)) {
            throw new NotFoundException("Booking not found");
        }
        if (StringUtils.hasText(booking.getPaymentReference())) {
            throw new ConflictException("This booking is already paid.");
        }
        if (booking.getStatus() != BookingStatus.REQUESTED && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("This request can no longer be cancelled.");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return toResponse(bookings.save(booking));
    }

    @Transactional
    public BookingResponse recordPayment(String customerUserId, Long bookingId, RecordBookingPaymentRequest request) {
        ExpertBooking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getCustomerUserId().equals(customerUserId)) {
            throw new NotFoundException("Booking not found");
        }
        String reference = request.paymentReference().trim();
        if (StringUtils.hasText(booking.getPaymentReference())) {
            if (booking.getPaymentReference().equals(reference)) {
                return toResponse(booking);
            }
            throw new ConflictException("This booking is already paid.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Pay only after the expert confirms the request.");
        }
        if (bookings.findByPaymentReference(reference).isPresent()) {
            throw new ConflictException("This payment is already linked to a booking.");
        }
        booking.setPaymentReference(reference);
        return toResponse(bookings.save(booking));
    }

    private void assertKnownService(ExpertProfile profile, String serviceTitle, String price) {
        List<ExpertServiceItem> offered = profile.getServices();
        if (offered == null || offered.isEmpty()) {
            return;
        }
        ExpertServiceItem match = offered.stream()
                .filter(item -> item.getTitle() != null && item.getTitle().trim().equalsIgnoreCase(serviceTitle))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Pick one of this expert's services."));
        if (!StringUtils.hasText(price) || !StringUtils.hasText(match.getPrice())) {
            return;
        }
        BigDecimal asked = amountOf(price);
        BigDecimal listed = amountOf(match.getPrice());
        if (asked != null && listed != null && asked.compareTo(listed) != 0) {
            throw new BadRequestException("That price does not match this service.");
        }
    }

    /** Seeker is asking. Messages are written for that person. */
    private void assertCreateSlot(ExpertProfile profile, String customerUserId, Instant when) {
        Instant windowStart = when.minus(VISIT);
        Instant windowEnd = when.plus(VISIT);
        if (bookings.countExpertOverlap(profile.getId(), HOLDING_THE_SLOT, null, windowStart, windowEnd) > 0) {
            throw new ConflictException("This expert is already booked at that time.");
        }
        if (bookings.countCustomerOverlap(profile.getUserId(), HOLDING_THE_SLOT, null, windowStart, windowEnd) > 0) {
            throw new ConflictException("This expert is already booked at that time.");
        }
        if (bookings.countCustomerOverlap(customerUserId, HOLDING_THE_SLOT, null, windowStart, windowEnd) > 0) {
            throw new ConflictException("You already have a booking at that time.");
        }
        if (bookings.countOwnedExpertOverlap(customerUserId, HOLDING_THE_SLOT, null, windowStart, windowEnd) > 0) {
            throw new ConflictException("You already have a job at that time.");
        }
    }

    /** Expert is replying. Messages are written for that person. */
    private void assertAcceptSlot(ExpertBooking booking) {
        Instant when = booking.getScheduledAt();
        Instant windowStart = when.minus(VISIT);
        Instant windowEnd = when.plus(VISIT);
        Long exceptId = booking.getId();
        ExpertProfile profile = booking.getProfile();
        if (bookings.countExpertOverlap(profile.getId(), HOLDING_THE_SLOT, exceptId, windowStart, windowEnd) > 0) {
            throw new ConflictException("This expert is already booked at that time.");
        }
        if (bookings.countCustomerOverlap(profile.getUserId(), HOLDING_THE_SLOT, exceptId, windowStart, windowEnd) > 0) {
            throw new ConflictException("You already have a booking at that time.");
        }
        if (bookings.countCustomerOverlap(booking.getCustomerUserId(), HOLDING_THE_SLOT, exceptId, windowStart, windowEnd) > 0) {
            throw new ConflictException("This customer already has a booking at that time.");
        }
        if (bookings.countOwnedExpertOverlap(booking.getCustomerUserId(), HOLDING_THE_SLOT, exceptId, windowStart, windowEnd) > 0) {
            throw new ConflictException("This customer already has a job at that time.");
        }
    }

    private static boolean matchesAny(String userId, Collection<String> keys) {
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
                booking.getCreatedAt(),
                booking.getPaymentReference(),
                merchantId
        );
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String preferredCustomerName(String supplied, String fromToken) {
        if (readableName(supplied)) {
            return supplied.trim();
        }
        if (readableName(fromToken)) {
            return fromToken.trim();
        }
        return "Customer";
    }

    /** A priced booking can be finished only after the customer pays through checkout. */
    private static boolean requiresPayment(String price) {
        if (!StringUtils.hasText(price)) {
            return false;
        }
        BigDecimal amount = amountOf(price);
        return amount != null && amount.signum() > 0;
    }

    private static BigDecimal amountOf(String price) {
        Matcher amount = AMOUNT.matcher(price.replace(",", ""));
        if (!amount.find()) {
            return null;
        }
        try {
            return new BigDecimal(amount.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean readableName(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return !value.trim().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static String trimTo(String value, int max, String fallback) {
        String text = StringUtils.hasText(value) ? value.trim() : fallback;
        return text.length() <= max ? text : text.substring(0, max);
    }
}
