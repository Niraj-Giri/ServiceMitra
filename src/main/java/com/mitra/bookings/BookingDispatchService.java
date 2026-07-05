package com.mitra.bookings;

import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled service that handles automatic provider assignment.
 *
 * Runs every 30 seconds and:
 * 1. Times out ASSIGNED bookings that haven't been accepted within 10 minutes
 * 2. Assigns providers to PENDING_DISPATCH bookings
 *
 * Design: Simple category-matching algorithm for MVP.
 * Phase 2: Add geo-distance sorting (nearest provider first).
 * Phase 3: Migrate to Kafka event-driven assignment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingDispatchService {

    private static final int ASSIGNMENT_TIMEOUT_MINUTES = 10;

    private final BookingRepository bookingRepository;
    private final ProviderRepository providerRepository;
    private final ServiceListingRepository serviceListingRepository;

    /**
     * Main dispatch loop. Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchBookings() {
        handleTimedOutAssignments();
        // assignPendingBookings(); // Disabled for now: customer chooses provider directly (TaskRabbit flow)
    }

    /**
     * Step 1: Find ASSIGNED bookings where provider hasn't responded in time.
     * Returns them to PENDING_DISPATCH for re-assignment.
     */
    private void handleTimedOutAssignments() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(ASSIGNMENT_TIMEOUT_MINUTES);
        List<Booking> timedOut = bookingRepository.findTimedOutAssignments(timeoutThreshold);

        for (Booking booking : timedOut) {
            log.warn("Booking {} timed out (assigned to provider {} at {}). Re-queuing.",
                    booking.getId(),
                    booking.getProvider() != null ? booking.getProvider().getId() : "unknown",
                    booking.getUpdatedAt());

            if (booking.getProvider() != null) {
                booking.addRejectedProvider(booking.getProvider().getId()); // skip on next attempt
            }
            booking.setProvider(null);
            booking.setStatus(BookingStatus.PENDING_DISPATCH);
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(booking);
        }

        if (!timedOut.isEmpty()) {
            log.info("Re-queued {} timed-out booking(s).", timedOut.size());
        }
    }

    /**
     * Step 2: Find PENDING_DISPATCH bookings and try to assign a provider.
     */
    private void assignPendingBookings() {
        List<Booking> pending = bookingRepository.findByStatusOrderByCreatedAtAsc(BookingStatus.PENDING_DISPATCH);

        for (Booking booking : pending) {
            tryAssignProvider(booking);
        }
    }

    /**
     * Attempts to assign the best available provider for a booking.
     * Skips providers who have already rejected this booking.
     */
    private void tryAssignProvider(Booking booking) {
        Optional<ServiceListing> serviceOpt = serviceListingRepository.findById(booking.getServiceId());
        if (serviceOpt.isEmpty()) {
            log.error("Booking {} references non-existent service ID {}. Skipping dispatch.",
                    booking.getId(), booking.getServiceId());
            return;
        }

        String category = serviceOpt.get().getCategory();

        // findEligibleProviders returns providers sorted by rating DESC, totalJobs DESC
        List<Provider> eligible = providerRepository.findEligibleProviders(category);

        for (Provider provider : eligible) {
            if (booking.hasProviderRejected(provider.getId())) {
                continue; // skip providers who already rejected this booking
            }

            // Found a match — assign
            booking.setProvider(provider);
            booking.setStatus(BookingStatus.ASSIGNED);
            booking.setUpdatedAt(LocalDateTime.now());
            bookingRepository.save(booking);

            log.info("Booking {} (service: {}) assigned to provider {} (rating: {}).",
                    booking.getId(), category, provider.getId(), provider.getRatingCache());

            // TODO Phase 2: Send push notification to provider
            return;
        }

        // No provider found — booking stays in PENDING_DISPATCH
        log.warn("No eligible provider found for booking {} (category: {}). Will retry next cycle.",
                booking.getId(), category);
    }
}
