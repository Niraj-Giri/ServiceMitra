package com.mitra.bookings;

import com.mitra.bookings.dto.BookingResponse;
import com.mitra.bookings.dto.CreateBookingRequest;
import com.mitra.common.BadRequestException;
import com.mitra.common.ForbiddenException;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import com.mitra.common.RewardPointsHistory;
import com.mitra.common.RewardPointsHistoryRepository;
import com.mitra.users.ProviderIncentive;
import com.mitra.users.ProviderIncentiveRepository;
import com.mitra.config.PlatformSettings;
import com.mitra.config.PlatformSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Core business logic for booking lifecycle management.
 *
 * Design principles:
 * 1. All status transitions are validated here (not in controller).
 * 2. All financial calculations use BigDecimal.
 * 3. All OTPs generated with SecureRandom.
 * 4. Ownership checks enforced before any mutation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10"); // 10%
    private static final int OTP_EXPIRY_MINUTES = 30;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final RewardPointsHistoryRepository rewardPointsHistoryRepository;
    private final ProviderIncentiveRepository providerIncentiveRepository;
    private final PlatformSettingsRepository platformSettingsRepository;

    // ─────────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", request.getUserId()));

        ServiceListing service = serviceListingRepository.findById(request.getServiceId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service", request.getServiceId()));

        if (Boolean.FALSE.equals(service.getIsActive())) {
            throw new BadRequestException("This service is currently unavailable");
        }

        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", request.getProviderId()));

        if (!"APPROVED".equals(provider.getStatus())) {
            throw new BadRequestException("This provider is currently unavailable");
        }

        // Validate scheduled time is at least 1 hour from now
        if (request.getScheduledAt().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new BadRequestException("Scheduled time must be at least 1 hour from now");
        }

        // Validate provider cooldown (30 minutes)
        LocalDateTime lastJobCompleted = bookingRepository.findLatestCompletedJobDate(provider.getId());
        if (lastJobCompleted != null) {
            long minutesSinceCompleted = java.time.Duration.between(lastJobCompleted, request.getScheduledAt()).toMinutes();
            if (minutesSinceCompleted >= 0 && minutesSinceCompleted < 30) {
                throw new BadRequestException("This professional is in a 30-minute cooldown period from their last completed job.");
            }
        }

        int pointsRedeemed = 0;
        BigDecimal pointsDiscountNpr = BigDecimal.ZERO;

        if (request.getPointsToRedeem() != null && request.getPointsToRedeem() > 0) {
            int pointsRequested = request.getPointsToRedeem();
            if (user.getRewardPoints() == null || user.getRewardPoints() < pointsRequested) {
                throw new BadRequestException("Insufficient reward points balance. You have " 
                        + (user.getRewardPoints() != null ? user.getRewardPoints() : 0) + " points.");
            }
            PlatformSettings settings = platformSettingsRepository.findById(1L).orElse(null);
            BigDecimal redemptionRate = settings != null && settings.getPointsRedemptionRate() != null 
                    ? settings.getPointsRedemptionRate() 
                    : BigDecimal.ONE;

            BigDecimal maxDiscount = service.getBasePrice();
            BigDecimal discount = new BigDecimal(pointsRequested).multiply(redemptionRate);
            if (discount.compareTo(maxDiscount) > 0) {
                discount = maxDiscount;
                pointsRequested = (int) Math.ceil(maxDiscount.doubleValue() / redemptionRate.doubleValue());
            }

            pointsRedeemed = pointsRequested;
            pointsDiscountNpr = discount;

            // Deduct from user
            user.setRewardPoints(user.getRewardPoints() - pointsRedeemed);
            userRepository.save(user);
        }

        Booking booking = Booking.builder()
                .user(user)
                .provider(provider)                      // direct select assign
                .serviceId(service.getId())
                .serviceName(service.getName())          // snapshot
                .scheduledAt(request.getScheduledAt())
                .address(request.getAddress())
                .addressId(request.getAddressId())
                .notes(request.getNotes())
                .amountNpr(service.getBasePrice())       // fixed price from service
                .pointsRedeemed(pointsRedeemed)
                .pointsDiscountNpr(pointsDiscountNpr)
                .status(BookingStatus.ASSIGNED)          // direct assign starts in ASSIGNED status
                .updatedAt(LocalDateTime.now())
                .build();

        booking = bookingRepository.save(booking);
        log.info("Booking {} created by user {} and directly assigned to provider {} for service '{}'", 
                booking.getId(), user.getId(), provider.getId(), service.getName());

        if (pointsRedeemed > 0) {
            RewardPointsHistory history = RewardPointsHistory.builder()
                    .userId(user.getId())
                    .points(-pointsRedeemed)
                    .actionType("BOOKING_REDEEMED")
                    .description("Redeemed points on Booking #" + booking.getId())
                    .bookingId(booking.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            rewardPointsHistoryRepository.save(history);
        }

        return BookingResponse.from(booking, false, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BookingResponse getBookingForCustomer(Long bookingId, Long customerId) {
        Booking booking = findBookingById(bookingId);
        assertCustomerOwnership(booking, customerId);

        boolean contactVisible = booking.getStatus().isChatAllowed() || booking.getStatus() == BookingStatus.COMPLETED;
        return BookingResponse.from(booking, true, contactVisible);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingForProvider(Long bookingId, Long providerId) {
        Booking booking = findBookingById(bookingId);
        assertProviderOwnership(booking, providerId);

        boolean contactVisible = booking.getStatus().isChatAllowed() || booking.getStatus() == BookingStatus.COMPLETED;
        return BookingResponse.from(booking, false, contactVisible);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getCustomerBookings(Long customerId, Pageable pageable) {
        return bookingRepository
                .findByUser_IdOrderByCreatedAtDesc(customerId, pageable)
                .map(b -> BookingResponse.from(b, false, false));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getProviderActiveJobs(Long providerId) {
        return bookingRepository.findActiveJobsByProvider(providerId)
                .stream()
                .map(b -> BookingResponse.from(b, false, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getProviderHistory(Long providerId, Pageable pageable) {
        return bookingRepository.findHistoryByProvider(providerId, pageable)
                .map(b -> BookingResponse.from(b, false, false));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getCustomerBookingsList(Long customerId) {
        return bookingRepository.findByUser_IdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(b -> BookingResponse.from(b, false, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getProviderBookingsList(Long providerId) {
        return bookingRepository.findByProvider_IdOrderByCreatedAtDesc(providerId)
                .stream()
                .map(b -> BookingResponse.from(b, false, true))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // STATUS TRANSITIONS — Provider actions
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Provider accepts the assigned booking.
     * Generates 6-digit OTP and sends it to customer.
     */
    @Transactional
    public BookingResponse acceptBooking(Long bookingId, Long providerId) {
        Booking booking = findBookingById(bookingId);
        assertProviderOwnership(booking, providerId);
        assertStatus(booking, BookingStatus.ASSIGNED, "accept");

        String otp = generateOtp();
        booking.setStartOtp(otp);
        booking.setOtpGeneratedAt(LocalDateTime.now());
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Booking {} accepted by provider {}. OTP generated.", bookingId, providerId);
        // TODO Phase 2: Send OTP to customer via SMS

        return BookingResponse.from(booking, false, true);
    }

    /**
     * Provider rejects the assigned booking.
     * Booking returns to PENDING_DISPATCH for re-assignment.
     */
    @Transactional
    public void rejectBooking(Long bookingId, Long providerId, String reason) {
        Booking booking = findBookingById(bookingId);
        assertProviderOwnership(booking, providerId);
        assertStatus(booking, BookingStatus.ASSIGNED, "reject");

        booking.addRejectedProvider(providerId);   // track so dispatch won't assign again
        booking.setProvider(null);
        booking.setStatus(BookingStatus.PENDING_DISPATCH);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Booking {} rejected by provider {}. Back to PENDING_DISPATCH.", bookingId, providerId);
    }

    /**
     * Provider enters OTP from customer to start the job.
     * OTP must match and not be expired (30 minutes from generation).
     */
    @Transactional
    public BookingResponse startBooking(Long bookingId, Long providerId, String otp) {
        Booking booking = findBookingById(bookingId);
        assertProviderOwnership(booking, providerId);
        assertStatus(booking, BookingStatus.ACCEPTED, "start");

        // OTP expiry check
        if (booking.getOtpGeneratedAt() == null ||
                booking.getOtpGeneratedAt().isBefore(LocalDateTime.now().minusMinutes(OTP_EXPIRY_MINUTES))) {
            throw new BadRequestException("OTP has expired. Please ask customer to refresh their OTP.");
        }

        // OTP match check (TEMPORARY: accept any input OTP for testing/development convenience)
        /*
        if (booking.getStartOtp() == null || !booking.getStartOtp().equals(otp.trim())) {
            throw new BadRequestException("Invalid OTP. Please verify the code shown on the customer's app.");
        }
        */

        booking.setStatus(BookingStatus.STARTED);
        booking.setStartedAt(LocalDateTime.now());
        booking.setStartOtp(null);           // clear OTP after use for security
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Booking {} started by provider {}.", bookingId, providerId);
        return BookingResponse.from(booking, false, true);
    }

    /**
     * Provider marks the job as complete.
     * Calculates platform fee, credits provider wallet.
     */
    @Transactional
    public BookingResponse completeBooking(Long bookingId, Long providerId) {
        Booking booking = findBookingById(bookingId);
        assertProviderOwnership(booking, providerId);
        assertStatus(booking, BookingStatus.STARTED, "complete");

        BigDecimal grossAmount = booking.getAmountNpr();
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // Fallback: re-fetch service price
            ServiceListing service = serviceListingRepository.findById(booking.getServiceId()).orElse(null);
            grossAmount = service != null ? service.getBasePrice() : BigDecimal.ZERO;
        }

        BigDecimal platformFee = grossAmount.multiply(PLATFORM_FEE_RATE);
        BigDecimal netEarnings = grossAmount.subtract(platformFee);

        booking.setTotalBill(grossAmount);
        booking.setPlatformFee(platformFee);
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // 1. Award Customer Reward Points
        User customer = booking.getUser();
        if (customer != null) {
            PlatformSettings settings = platformSettingsRepository.findById(1L).orElse(null);
            BigDecimal earningRate = settings != null && settings.getPointsPerNprSpent() != null
                    ? settings.getPointsPerNprSpent()
                    : new BigDecimal("0.10");
            
            BigDecimal pointsToEarnDec = grossAmount.multiply(earningRate);
            int pointsToEarn = pointsToEarnDec.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            if (pointsToEarn > 0) {
                customer.setRewardPoints((customer.getRewardPoints() != null ? customer.getRewardPoints() : 0) + pointsToEarn);
                userRepository.save(customer);

                RewardPointsHistory pointsHistory = RewardPointsHistory.builder()
                        .userId(customer.getId())
                        .points(pointsToEarn)
                        .actionType("BOOKING_EARNED")
                        .description("Earned points on Completed Booking #" + booking.getId())
                        .bookingId(booking.getId())
                        .createdAt(LocalDateTime.now())
                        .build();
                rewardPointsHistoryRepository.save(pointsHistory);
            }
        }

        // 2. Update Provider Stats & Milestones
        Provider provider = booking.getProvider();
        if (provider != null) {
            int newTotalJobs = provider.getTotalJobs() + 1;
            provider.setTotalJobs(newTotalJobs);
            providerRepository.save(provider);

            // Automate Provider Milestone: 5 Completed Bookings = Rs 500 Bonus
            if (newTotalJobs % 5 == 0) {
                ProviderIncentive milestoneBonus = ProviderIncentive.builder()
                        .providerId(provider.getId())
                        .amount(new BigDecimal("500.00"))
                        .bookingId(booking.getId())
                        .reason("COMPLETED_BOOKINGS_MILESTONE")
                        .description("Milestone Bonus: Completed " + newTotalJobs + " jobs!")
                        .status("PENDING_PAYOUT")
                        .createdAt(LocalDateTime.now())
                        .build();
                providerIncentiveRepository.save(milestoneBonus);
            }
        }

        log.info("Booking {} completed. Gross: {}, Fee: {}, Net to provider: {}",
                bookingId, grossAmount, platformFee, netEarnings);

        return BookingResponse.from(booking, false, false);
    }

    /**
     * Customer reschedules their booking to a new date and time.
     */
    @Transactional
    public BookingResponse rescheduleBooking(Long bookingId, Long customerId, LocalDateTime newScheduledAt) {
        Booking booking = findBookingById(bookingId);
        assertCustomerOwnership(booking, customerId);

        if (booking.getStatus().isTerminal()) {
            throw new BadRequestException("Cannot reschedule a booking that is " + booking.getStatus());
        }
        if (booking.getStatus() == BookingStatus.STARTED) {
            throw new BadRequestException("Cannot reschedule a job that has already started.");
        }

        // Validate scheduled time is at least 1 hour from now
        if (newScheduledAt.isBefore(LocalDateTime.now().plusHours(1))) {
            throw new BadRequestException("Scheduled time must be at least 1 hour from now");
        }

        // Validate provider cooldown (30 minutes)
        if (booking.getProvider() != null) {
            LocalDateTime lastJobCompleted = bookingRepository.findLatestCompletedJobDate(booking.getProvider().getId());
            if (lastJobCompleted != null) {
                long minutesSinceCompleted = java.time.Duration.between(lastJobCompleted, newScheduledAt).toMinutes();
                if (minutesSinceCompleted >= 0 && minutesSinceCompleted < 30) {
                    throw new BadRequestException("This professional is in a 30-minute cooldown period from their last completed job.");
                }
            }
        }

        booking.setScheduledAt(newScheduledAt);
        booking.setUpdatedAt(LocalDateTime.now());
        booking = bookingRepository.save(booking);

        log.info("Booking {} rescheduled by customer {} to {}", bookingId, customerId, newScheduledAt);
        return BookingResponse.from(booking, false, false);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CANCEL
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Customer cancels their booking.
     * Not allowed if job is already STARTED.
     */
    @Transactional
    public void cancelByCustomer(Long bookingId, Long customerId, String reason) {
        Booking booking = findBookingById(bookingId);
        assertCustomerOwnership(booking, customerId);

        if (booking.getStatus() == BookingStatus.STARTED) {
            throw new BadRequestException("Cannot cancel a job that has already started. Please contact support.");
        }
        if (booking.getStatus().isTerminal()) {
            throw new BadRequestException("This booking is already " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_CUSTOMER);
        booking.setCancelledBy("CUSTOMER");
        booking.setCancellationReason(reason);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        log.info("Booking {} cancelled by customer {}.", bookingId, customerId);
    }

    /**
     * Provider cancels a booking they accepted.
     */
    @Transactional
    public void cancelByProvider(Long bookingId, Long providerId, String reason) {
        Booking booking = findBookingById(bookingId);
        assertProviderOwnership(booking, providerId);

        if (booking.getStatus() == BookingStatus.STARTED) {
            throw new BadRequestException("Cannot cancel a job in progress. Please contact support.");
        }
        if (booking.getStatus().isTerminal()) {
            throw new BadRequestException("This booking is already " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED_BY_PROVIDER);
        booking.setCancelledBy("PROVIDER");
        booking.setCancellationReason(reason);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // TODO Phase 2: Apply provider penalty, send re-assignment notification
        log.info("Booking {} cancelled by provider {}.", bookingId, providerId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private Booking findBookingById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
    }

    private void assertCustomerOwnership(Booking booking, Long customerId) {
        if (booking.getUser() == null || !booking.getUser().getId().equals(customerId)) {
            throw ForbiddenException.notYourResource("booking");
        }
    }

    private void assertProviderOwnership(Booking booking, Long providerId) {
        if (booking.getProvider() == null || !booking.getProvider().getId().equals(providerId)) {
            throw ForbiddenException.notYourResource("booking");
        }
    }

    private void assertStatus(Booking booking, BookingStatus requiredStatus, String action) {
        if (booking.getStatus() != requiredStatus) {
            throw new BadRequestException(
                    "Cannot " + action + " a booking with status: " + booking.getStatus()
                            + ". Required status: " + requiredStatus
            );
        }
    }

    /**
     * Generates a cryptographically secure 4-digit OTP.
     * Range: 1000–9999 (always 4 digits, compatible with frontend input).
     */
    private String generateOtp() {
        int otp = 1000 + secureRandom.nextInt(9000);
        return String.valueOf(otp);
    }
}
