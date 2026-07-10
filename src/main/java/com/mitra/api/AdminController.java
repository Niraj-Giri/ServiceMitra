package com.mitra.api;

import com.mitra.bookings.*;
import com.mitra.bookings.dto.BookingResponse;
import com.mitra.common.*;
import com.mitra.config.PlatformSettings;
import com.mitra.config.PlatformSettingsRepository;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.users.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Transactional(readOnly = true)
public class AdminController {

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final AuditLogRepository auditLogRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository complaintMessageRepository;
    private final TransactionRepository transactionRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final RewardPointsHistoryRepository rewardPointsHistoryRepository;
    private final ProviderIncentiveRepository providerIncentiveRepository;

    // Helper for audit logs
    private void logAdminAction(String action, String entity, String oldValue, String newValue) {
        try {
            AuditLog audit = AuditLog.builder()
                    .admin("Admin User")
                    .action(action)
                    .entity(entity)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .timestamp(LocalDateTime.now())
                    .ipAddress("127.0.0.1")
                    .build();
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }

    // Helper to log booking history transitions
    private void logBookingStatusHistory(Long bookingId, String status, String notes) {
        try {
            BookingStatusHistory history = BookingStatusHistory.builder()
                    .bookingId(bookingId)
                    .status(status)
                    .updatedAt(LocalDateTime.now())
                    .updatedBy("ADMIN")
                    .notes(notes)
                    .build();
            bookingStatusHistoryRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to write booking status history: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ANALYTICS & DASHBOARD
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/analytics/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        long totalCustomers = userRepository.count();
        long totalProviders = providerRepository.count();
        long onlineProviders = providerRepository.countByIsOnline(true);
        long pendingReviews = providerRepository.countByStatus("PENDING_REVIEW");
        long totalBookings = bookingRepository.count();
        long completedBookings = bookingRepository.countByStatus(BookingStatus.COMPLETED);
        long activeBookings = totalBookings - completedBookings - bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_CUSTOMER) 
                - bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_PROVIDER) - bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_ADMIN);
        
        // Sum total revenue from platform commission
        BigDecimal totalCommission = transactionRepository.sumTotalCommission();

        long pendingComplaints = complaintRepository.countByStatus("PENDING");

        Map<String, Object> summary = Map.of(
                "totalCustomers", totalCustomers,
                "totalProviders", totalProviders,
                "onlineProviders", onlineProviders,
                "pendingProviderReviews", pendingReviews,
                "totalBookings", totalBookings,
                "completedBookings", completedBookings,
                "activeBookings", activeBookings,
                "totalRevenue", totalCommission,
                "pendingComplaints", pendingComplaints
        );
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/analytics/charts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChartsData() {
        // Bookings per Day mock values for chart
        List<Map<String, Object>> bookingsPerDay = List.of(
                Map.of("date", "2026-07-01", "count", 4),
                Map.of("date", "2026-07-02", "count", 8),
                Map.of("date", "2026-07-03", "count", 5),
                Map.of("date", "2026-07-04", "count", 11),
                Map.of("date", "2026-07-05", "count", 14),
                Map.of("date", "2026-07-06", "count", 9)
        );

        // Revenue per Month
        List<Map<String, Object>> revenuePerMonth = List.of(
                Map.of("month", "Jan", "amount", 12000),
                Map.of("month", "Feb", "amount", 18500),
                Map.of("month", "Mar", "amount", 22000),
                Map.of("month", "Apr", "amount", 25000),
                Map.of("month", "May", "amount", 31000),
                Map.of("month", "Jun", "amount", 42000)
        );

        // Top services
        List<Map<String, Object>> topServices = List.of(
                Map.of("name", "Fan Repair", "bookings", 45),
                Map.of("name", "Tap Repair", "bookings", 38),
                Map.of("name", "AC Filter Clean", "bookings", 29),
                Map.of("name", "Sofa Cleaning", "bookings", 15)
        );

        Map<String, Object> charts = Map.of(
                "bookingsPerDay", bookingsPerDay,
                "revenuePerMonth", revenuePerMonth,
                "topServices", topServices
        );
        return ResponseEntity.ok(ApiResponse.success(charts));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<List<User>>> getCustomers() {
        return ResponseEntity.ok(ApiResponse.success(userRepository.findAll()));
    }

    @PutMapping("/customers/{id}/suspend")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> suspendCustomer(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        user.setIsActive(false);
        userRepository.save(user);
        logAdminAction("Suspended customer: " + user.getName(), "User", "ACTIVE", "SUSPENDED");
        return ResponseEntity.ok(ApiResponse.success(null, "Customer suspended successfully"));
    }

    @PutMapping("/customers/{id}/activate")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> activateCustomer(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        user.setIsActive(true);
        userRepository.save(user);
        logAdminAction("Activated customer: " + user.getName(), "User", "SUSPENDED", "ACTIVE");
        return ResponseEntity.ok(ApiResponse.success(null, "Customer activated successfully"));
    }

    @PutMapping("/customers/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<User>> updateCustomer(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));

        String oldVal = user.getName() + " (" + user.getEmail() + ")";
        if (payload.containsKey("name")) user.setName((String) payload.get("name"));
        if (payload.containsKey("email")) user.setEmail((String) payload.get("email"));
        userRepository.save(user);
        String newVal = user.getName() + " (" + user.getEmail() + ")";

        logAdminAction("Updated customer details for: " + user.getName(), "User", oldVal, newVal);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @DeleteMapping("/customers/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        userRepository.delete(user);
        logAdminAction("Deleted customer account: " + user.getName(), "User", user.getName(), "DELETED");
        return ResponseEntity.ok(ApiResponse.success(null, "Customer account deleted"));
    }

    @GetMapping("/customers/{id}/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getCustomerBookings(@PathVariable Long id) {
        List<BookingResponse> responses = bookingRepository.findByUser_IdOrderByCreatedAtDesc(id)
                .stream()
                .map(b -> BookingResponse.from(b, true, true))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/customers/{id}/loyalty")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerLoyalty(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        List<RewardPointsHistory> history = rewardPointsHistoryRepository.findByUserIdOrderByCreatedAtDesc(id);
        Map<String, Object> data = new HashMap<>();
        data.put("pointsBalance", user.getRewardPoints() != null ? user.getRewardPoints() : 0);
        data.put("history", history);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/customers/{id}/loyalty")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> adjustCustomerPoints(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));

        int points = Integer.parseInt(payload.get("points").toString());
        String actionType = (String) payload.get("actionType");
        String description = (String) payload.get("description");

        int currentBalance = user.getRewardPoints() != null ? user.getRewardPoints() : 0;
        user.setRewardPoints(Math.max(0, currentBalance + points));
        userRepository.save(user);

        RewardPointsHistory history = RewardPointsHistory.builder()
                .userId(user.getId())
                .points(points)
                .actionType(actionType != null ? actionType : "PROMOTION")
                .description(description != null ? description : "Manual adjustment by Admin")
                .createdAt(LocalDateTime.now())
                .build();
        rewardPointsHistoryRepository.save(history);

        logAdminAction("Adjusted customer points for " + user.getName() + " by " + points, "User", String.valueOf(currentBalance), String.valueOf(user.getRewardPoints()));
        return ResponseEntity.ok(ApiResponse.success(null, "Customer points adjusted successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<Provider>>> getProviders(
            @RequestParam(required = false) String status) {
        List<Provider> providers = status != null
                ? providerRepository.findByStatusOrderByCreatedAtDesc(status)
                : providerRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    @GetMapping("/providers/pending")
    public ResponseEntity<ApiResponse<List<Provider>>> getPendingProviders() {
        List<Provider> providers = providerRepository.findByStatusOrderByCreatedAtDesc("PENDING_REVIEW");
        return ResponseEntity.ok(ApiResponse.success(providers));
    }

    @PutMapping("/providers/{id}/approve")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> approveProvider(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        provider.setStatus("APPROVED");
        provider.setIsOnline(true);
        provider.setUpdatedAt(LocalDateTime.now());
        if (body != null && body.containsKey("notes")) provider.setAdminNotes(body.get("notes"));
        providerRepository.save(provider);
        logAdminAction("Approved provider profile: " + provider.getName(), "Provider", "PENDING_REVIEW", "APPROVED");
        return ResponseEntity.ok(ApiResponse.success(null, "Provider approved successfully"));
    }

    @PutMapping("/providers/{id}/reject")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> rejectProvider(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        provider.setStatus("REJECTED");
        provider.setAdminNotes(body.get("reason"));
        provider.setUpdatedAt(LocalDateTime.now());
        providerRepository.save(provider);
        logAdminAction("Rejected provider application: " + provider.getName(), "Provider", "PENDING_REVIEW", "REJECTED");
        return ResponseEntity.ok(ApiResponse.success(null, "Provider application rejected"));
    }

    @PutMapping("/providers/{id}/suspend")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> suspendProvider(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        provider.setStatus("SUSPENDED");
        provider.setIsOnline(false);
        provider.setAdminNotes(body.get("reason"));
        provider.setUpdatedAt(LocalDateTime.now());
        providerRepository.save(provider);
        logAdminAction("Suspended provider: " + provider.getName(), "Provider", "APPROVED", "SUSPENDED");
        return ResponseEntity.ok(ApiResponse.success(null, "Provider suspended"));
    }

    @PutMapping("/providers/{id}/activate")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> activateProvider(@PathVariable Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        provider.setStatus("APPROVED");
        provider.setIsOnline(true);
        provider.setUpdatedAt(LocalDateTime.now());
        providerRepository.save(provider);
        logAdminAction("Re-activated provider: " + provider.getName(), "Provider", "SUSPENDED", "APPROVED");
        return ResponseEntity.ok(ApiResponse.success(null, "Provider activated successfully"));
    }

    @PutMapping("/providers/{id}/commission")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> updateProviderCommission(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        BigDecimal rate = new BigDecimal(payload.get("commissionPercentage").toString());
        BigDecimal oldRate = provider.getCommissionPercentage();
        provider.setCommissionPercentage(rate);
        providerRepository.save(provider);
        logAdminAction("Changed commission for provider " + provider.getName(), "Provider", String.valueOf(oldRate), String.valueOf(rate));
        return ResponseEntity.ok(ApiResponse.success(null, "Provider commission updated"));
    }

    @PutMapping("/providers/{id}/assign-area")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> updateProviderArea(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        String address = (String) payload.get("address");
        provider.setAddress(address);
        providerRepository.save(provider);
        logAdminAction("Assigned area to provider " + provider.getName(), "Provider", "", address);
        return ResponseEntity.ok(ApiResponse.success(null, "Provider area assigned"));
    }

    @GetMapping("/providers/{id}/earnings")
    public ResponseEntity<ApiResponse<List<Transaction>>> getProviderEarnings(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(transactionRepository.findByProviderIdOrderByCreatedAtDesc(id)));
    }

    @GetMapping("/providers/{id}/incentives")
    public ResponseEntity<ApiResponse<List<ProviderIncentive>>> getProviderIncentivesAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(providerIncentiveRepository.findByProviderIdOrderByCreatedAtDesc(id)));
    }

    @PostMapping("/providers/{id}/incentives")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> awardProviderIncentive(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));

        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String reason = (String) payload.get("reason");
        String description = (String) payload.get("description");

        ProviderIncentive incentive = ProviderIncentive.builder()
                .providerId(provider.getId())
                .amount(amount)
                .reason(reason != null ? reason : "SPECIAL_CAMPAIGN")
                .description(description != null ? description : "Manual performance bonus")
                .status("PENDING_PAYOUT")
                .createdAt(LocalDateTime.now())
                .build();
        providerIncentiveRepository.save(incentive);

        logAdminAction("Awarded manual incentive to provider " + provider.getName() + " of Rs. " + amount, "Provider", "", String.valueOf(amount));
        return ResponseEntity.ok(ApiResponse.success(null, "Incentive bonus awarded successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BOOKING MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Booking> bookings = status != null
                ? bookingRepository.findByStatusOrderByCreatedAtDesc(
                        BookingStatus.valueOf(status), PageRequest.of(page, size))
                : bookingRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        
        Page<BookingResponse> responses = bookings.map(b -> BookingResponse.from(b, true, true));
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/bookings/{id}/assign")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> manuallyAssignProvider(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        Provider provider = providerRepository.findById(body.get("providerId"))
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", body.get("providerId")));

        booking.setProvider(provider);
        booking.setStatus(BookingStatus.ASSIGNED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        logAdminAction("Manually assigned provider to booking #" + id, "Booking", "", provider.getName());
        logBookingStatusHistory(id, "ASSIGNED", "Provider manually assigned: " + provider.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Provider manually assigned"));
    }

    @PutMapping("/bookings/{id}/reschedule")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> rescheduleBooking(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        LocalDateTime newTime = LocalDateTime.parse(body.get("scheduledAt"));
        LocalDateTime oldTime = booking.getScheduledAt();
        booking.setScheduledAt(newTime);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        logAdminAction("Rescheduled booking #" + id, "Booking", String.valueOf(oldTime), String.valueOf(newTime));
        logBookingStatusHistory(id, String.valueOf(booking.getStatus()), "Booking rescheduled by Admin to " + newTime);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking rescheduled successfully"));
    }

    @PutMapping("/bookings/{id}/cancel")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        booking.setStatus(BookingStatus.CANCELLED_BY_ADMIN);
        booking.setCancelledBy("ADMIN");
        booking.setCancellationReason(body.getOrDefault("reason", "Cancelled by administrator"));
        booking.setCancelledAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);
        logAdminAction("Cancelled booking #" + id, "Booking", "ACTIVE", "CANCELLED_BY_ADMIN");
        logBookingStatusHistory(id, "CANCELLED_BY_ADMIN", "Booking cancelled by Admin. Reason: " + body.get("reason"));
        return ResponseEntity.ok(ApiResponse.success(null, "Booking cancelled successfully"));
    }

    @PutMapping("/bookings/{id}/refund")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> refundBooking(@PathVariable Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        
        logAdminAction("Issued refund for booking #" + id, "Booking", "PAID", "REFUNDED");
        logBookingStatusHistory(id, String.valueOf(booking.getStatus()), "Refund processed and issued.");
        return ResponseEntity.ok(ApiResponse.success(null, "Refund processed successfully"));
    }

    @PostMapping("/bookings/{id}/notes")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> addInternalNote(@PathVariable Long id, @RequestBody Map<String, String> body) {
        logAdminAction("Added internal note to booking #" + id, "Booking", "", body.get("note"));
        logBookingStatusHistory(id, "NOTE_ADDED", "Internal Note: " + body.get("note"));
        return ResponseEntity.ok(ApiResponse.success(null, "Internal note saved"));
    }

    @GetMapping("/bookings/{id}/timeline")
    public ResponseEntity<ApiResponse<List<BookingStatusHistory>>> getBookingTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingStatusHistoryRepository.findByBookingIdOrderByUpdatedAtAsc(id)));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SERVICE MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<List<ServiceListing>>> getServices() {
        return ResponseEntity.ok(ApiResponse.success(serviceListingRepository.findAll()));
    }

    @PostMapping("/services")
    @Transactional
    public ResponseEntity<ApiResponse<ServiceListing>> createService(
            @Valid @RequestBody CreateServiceRequest request) {
        ServiceListing service = ServiceListing.builder()
                .category(request.getCategory())
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .priceType("FIXED")
                .durationMin(request.getDurationMin())
                .whatIncluded(request.getWhatIncluded())
                .whatExcluded(request.getWhatExcluded())
                .isActive(true)
                .build();
        service = serviceListingRepository.save(service);
        logAdminAction("Created new service: " + service.getName(), "ServiceListing", "", service.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service));
    }

    @PutMapping("/services/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<ServiceListing>> updateService(
            @PathVariable Long id,
            @RequestBody CreateServiceRequest request) {
        ServiceListing service = serviceListingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Service", id));
        if (request.getName() != null) service.setName(request.getName());
        if (request.getDescription() != null) service.setDescription(request.getDescription());
        if (request.getBasePrice() != null) service.setBasePrice(request.getBasePrice());
        if (request.getDurationMin() != null) service.setDurationMin(request.getDurationMin());
        if (request.getWhatIncluded() != null) service.setWhatIncluded(request.getWhatIncluded());
        if (request.getWhatExcluded() != null) service.setWhatExcluded(request.getWhatExcluded());
        service = serviceListingRepository.save(service);
        logAdminAction("Updated service listing: " + service.getName(), "ServiceListing", "", service.getName());
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/services/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivateService(@PathVariable Long id) {
        ServiceListing service = serviceListingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Service", id));
        service.setIsActive(false);
        serviceListingRepository.save(service);
        logAdminAction("Deactivated service listing: " + service.getName(), "ServiceListing", "ACTIVE", "DEACTIVATED");
        return ResponseEntity.ok(ApiResponse.success(null, "Service deactivated"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PAYMENTS & PAYOUTS
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/payments/transactions")
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactions() {
        return ResponseEntity.ok(ApiResponse.success(transactionRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/payments/payouts")
    public ResponseEntity<ApiResponse<List<PayoutRequest>>> getPayoutRequests() {
        return ResponseEntity.ok(ApiResponse.success(payoutRequestRepository.findAllByOrderByCreatedAtDesc()));
    }

    @PutMapping("/payments/payouts/{id}/release")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> releasePayout(@PathVariable Long id) {
        PayoutRequest req = payoutRequestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PayoutRequest", id));
        req.setStatus("RELEASED");
        payoutRequestRepository.save(req);

        // Mark provider's pending incentives as PAID and link to this payout request
        List<ProviderIncentive> pendingIncentives = providerIncentiveRepository.findByProviderIdAndStatus(req.getProviderId(), "PENDING_PAYOUT");
        for (ProviderIncentive incentive : pendingIncentives) {
            incentive.setStatus("PAID");
            incentive.setPayoutId(id);
            providerIncentiveRepository.save(incentive);
        }

        logAdminAction("Released payout request #" + id, "PayoutRequest", "PENDING", "RELEASED");
        return ResponseEntity.ok(ApiResponse.success(null, "Payout released successfully"));
    }

    @PutMapping("/payments/payouts/{id}/hold")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> holdPayout(@PathVariable Long id) {
        PayoutRequest req = payoutRequestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PayoutRequest", id));
        req.setStatus("HELD");
        payoutRequestRepository.save(req);
        logAdminAction("Placed hold on payout request #" + id, "PayoutRequest", "PENDING", "HELD");
        return ResponseEntity.ok(ApiResponse.success(null, "Payout placed on hold"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // COMPLAINTS
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<Complaint>>> getComplaints() {
        return ResponseEntity.ok(ApiResponse.success(complaintRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/complaints/{id}/messages")
    public ResponseEntity<ApiResponse<List<ComplaintMessage>>> getComplaintMessages(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintMessageRepository.findByComplaintIdOrderByCreatedAtAsc(id)));
    }

    @PostMapping("/complaints/{id}/messages")
    @Transactional
    public ResponseEntity<ApiResponse<ComplaintMessage>> addComplaintMessage(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ComplaintMessage message = ComplaintMessage.builder()
                .complaintId(id)
                .senderId(1L) // Admin placeholder
                .senderRole("ADMIN")
                .content(body.get("content"))
                .createdAt(LocalDateTime.now())
                .build();
        message = complaintMessageRepository.save(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(message));
    }

    @PutMapping("/complaints/{id}/resolve")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> resolveComplaint(@PathVariable Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));
        complaint.setStatus("RESOLVED");
        complaint.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(complaint);
        logAdminAction("Resolved complaint #" + id, "Complaint", "PENDING", "RESOLVED");
        return ResponseEntity.ok(ApiResponse.success(null, "Complaint resolved successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BROADCAST NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────────

    @PostMapping("/notifications/broadcast")
    public ResponseEntity<ApiResponse<Void>> sendBroadcast(@RequestBody Map<String, String> payload) {
        String target = payload.get("target"); // ALL_CUSTOMERS, ALL_PROVIDERS, SELECTED_USERS
        String type = payload.get("type"); // PUSH, EMAIL, SMS
        String content = payload.get("content");
        
        log.info("Mock broadcast sent to: {} using {}. Content: {}", target, type, content);
        logAdminAction("Sent broadcast notification to: " + target, "Notification", "", content);
        return ResponseEntity.ok(ApiResponse.success(null, "Notification broadcast sent successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PLATFORM SETTINGS
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<PlatformSettings>> getSettings() {
        PlatformSettings settings = platformSettingsRepository.findById(1L)
                .orElseGet(() -> {
                    PlatformSettings s = PlatformSettings.builder()
                            .platformName("ServiceMitra")
                            .commissionPercentage(BigDecimal.valueOf(10.00))
                            .supportNumber("9800000000")
                            .bookingRadius(15.0)
                            .workingHours("08:00-20:00")
                            .autoAssignmentRules("CLOSEST_DISTANCE")
                            .taxSettings("NONE")
                            .paymentGateway("COD")
                            .build();
                    return platformSettingsRepository.save(s);
                });
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping("/settings")
    @Transactional
    public ResponseEntity<ApiResponse<PlatformSettings>> updateSettings(@RequestBody Map<String, Object> payload) {
        PlatformSettings settings = platformSettingsRepository.findById(1L)
                .orElseThrow(() -> ResourceNotFoundException.of("PlatformSettings", 1L));

        if (payload.containsKey("platformName")) settings.setPlatformName((String) payload.get("platformName"));
        if (payload.containsKey("commissionPercentage")) {
            settings.setCommissionPercentage(new BigDecimal(payload.get("commissionPercentage").toString()));
        }
        if (payload.containsKey("supportNumber")) settings.setSupportNumber((String) payload.get("supportNumber"));
        if (payload.containsKey("cancellationPolicy")) settings.setCancellationPolicy((String) payload.get("cancellationPolicy"));
        if (payload.containsKey("bookingRadius")) {
            settings.setBookingRadius(Double.valueOf(payload.get("bookingRadius").toString()));
        }
        if (payload.containsKey("workingHours")) settings.setWorkingHours((String) payload.get("workingHours"));
        if (payload.containsKey("paymentGateway")) settings.setPaymentGateway((String) payload.get("paymentGateway"));

        platformSettingsRepository.save(settings);
        logAdminAction("Updated global platform configurations", "PlatformSettings", "", "UPDATED");
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // AUDIT LOGS
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(auditLogRepository.findAllByOrderByTimestampDesc()));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INNER REQUEST CLASSES
    // ─────────────────────────────────────────────────────────────────────────────

    @Data
    public static class CreateServiceRequest {
        @NotBlank(message = "Category is required")
        private String category;

        @NotBlank(message = "Service name is required")
        private String name;

        private String description;

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.01", message = "Price must be positive")
        private BigDecimal basePrice;

        private Integer durationMin;
        private String whatIncluded;
        private String whatExcluded;
    }
}
