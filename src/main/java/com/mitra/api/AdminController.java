package com.mitra.api;

import com.mitra.bookings.*;
import com.mitra.bookings.dto.BookingResponse;
import com.mitra.common.*;
import com.mitra.config.PlatformSettings;
import com.mitra.config.PlatformSettingsRepository;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.users.*;
import com.mitra.reviews.Review;
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
// SEC-05: @CrossOrigin removed - CORS is centrally managed in SecurityConfig
@Transactional(readOnly = true)
public class AdminController {

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final com.mitra.taskrequests.TaskRequestRepository taskRequestRepository;
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
    private final com.mitra.reviews.ReviewRepository reviewRepository;
    private final ProviderStrikeRepository providerStrikeRepository;
    private final ProviderWalletRepository providerWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final BroadcastNotificationRepository broadcastNotificationRepository;
    private final ComplaintAdminNoteRepository complaintAdminNoteRepository;

    // Helper for audit logs
    private String getClientIp() {
        try {
            jakarta.servlet.http.HttpServletRequest request =
                ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    /**
     * SEC-04: Extract the real admin identity from the Spring Security context.
     * Previously this returned the hardcoded string "Admin User", meaning audit
     * logs were useless - you could never tell WHICH admin performed an action.
     */
    private String getAdminIdentity() {
        try {
            Object principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
            // Principal is set to the user ID (Long) by JwtAuthFilter
            if (principal instanceof Long) {
                return "Admin#" + principal;
            }
            return principal != null ? principal.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void logAdminAction(String action, String entity, String oldValue, String newValue) {
        logAdminAction(action, entity, oldValue, newValue, null);
    }

    private void logAdminAction(String action, String entity, String oldValue, String newValue, String reason) {
        try {
            AuditLog audit = AuditLog.builder()
                    .admin(getAdminIdentity())   // SEC-04: real identity, not hardcoded "Admin User"
                    .action(action)
                    .entity(entity)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .timestamp(LocalDateTime.now())
                    .ipAddress(getClientIp())
                    .reason(reason)
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

    // -----------------------------------------------------------------------------
    // ANALYTICS & DASHBOARD
    // -----------------------------------------------------------------------------

    @GetMapping("/analytics/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        long totalCustomers = userRepository.count();
        long totalProviders = providerRepository.count();
        long onlineProviders = providerRepository.countByIsOnline(true);
        long pendingReviews = providerRepository.countByStatus("PENDING_REVIEW");

        // Bidding/Task requests counts
        long totalTasks = taskRequestRepository.count();
        long completedTasks = taskRequestRepository.countByStatus(com.mitra.taskrequests.TaskRequestStatus.COMPLETED);
        long activeTasks = taskRequestRepository.countByStatus(com.mitra.taskrequests.TaskRequestStatus.ACCEPTED)
                + taskRequestRepository.countByStatus(com.mitra.taskrequests.TaskRequestStatus.STARTED)
                + taskRequestRepository.countByStatus(com.mitra.taskrequests.TaskRequestStatus.OPEN)
                + taskRequestRepository.countByStatus(com.mitra.taskrequests.TaskRequestStatus.QUOTING);
        long cancelledTasks = taskRequestRepository.countByStatus(com.mitra.taskrequests.TaskRequestStatus.CANCELLED);

        // Direct bookings counts
        long totalDirectBookings = bookingRepository.count();
        long completedDirectBookings = bookingRepository.countByStatus(BookingStatus.COMPLETED);
        long activeDirectBookings = totalDirectBookings - completedDirectBookings 
                - bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_CUSTOMER) 
                - bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_PROVIDER) 
                - bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_ADMIN);
        long cancelledDirectBookings = bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_CUSTOMER)
                + bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_PROVIDER)
                + bookingRepository.countByStatus(BookingStatus.CANCELLED_BY_ADMIN);

        // Combined values
        long totalBookings = totalDirectBookings + totalTasks;
        long completedBookings = completedDirectBookings + completedTasks;
        long activeBookings = activeDirectBookings + activeTasks;
        long cancelledBookings = cancelledDirectBookings + cancelledTasks;

        // Sum total revenue from platform commission
        BigDecimal totalCommission = transactionRepository.sumTotalCommission();

        long pendingComplaints = complaintRepository.countByStatus("PENDING");

        LocalDateTime startOfToday = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long todayBookings = bookingRepository.countBookingsCreatedAfter(startOfToday)
                + taskRequestRepository.countTasksCreatedAfter(startOfToday);
        BigDecimal todayRevenue = transactionRepository.sumCommissionSince(startOfToday);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCustomers", totalCustomers);
        summary.put("totalProviders", totalProviders);
        summary.put("onlineProviders", onlineProviders);
        summary.put("pendingProviderReviews", pendingReviews);
        summary.put("totalBookings", totalBookings);
        summary.put("completedBookings", completedBookings);
        summary.put("activeBookings", activeBookings);
        summary.put("totalRevenue", totalCommission);
        summary.put("pendingComplaints", pendingComplaints);
        summary.put("todayBookings", todayBookings);
        summary.put("cancelledBookings", cancelledBookings);
        summary.put("todayRevenue", todayRevenue);

        // Actionable operational queues
        long pendingRefundRequests = complaintRepository.countByCategoryAndStatus("REFUND_ISSUE", "PENDING")
                + transactionRepository.countByStatus("PENDING_REFUND");
        long pendingPayoutRequests = payoutRequestRepository.countByStatus("PENDING");
        long bookingsWithoutProvider = bookingRepository.countByProviderIsNullAndStatus(BookingStatus.PENDING_DISPATCH);
        long emergencyBookings = bookingRepository.countByStatusInAndScheduledAtBefore(
                java.util.List.of(BookingStatus.PENDING_DISPATCH, BookingStatus.ASSIGNED),
                LocalDateTime.now().plusHours(2));
        long highPriorityComplaints = complaintRepository.countByPriorityAndStatus("HIGH", "PENDING");
        long suspendedProvidersAwaitingReview = providerRepository.countByStatus("SUSPENDED");
        long providersWithHighComplaintRate = complaintRepository.findAll().stream()
                .filter(c -> c.getProviderId() != null)
                .collect(java.util.stream.Collectors.groupingBy(com.mitra.common.Complaint::getProviderId, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .count();
        long customersWithFraudRisk = userRepository.countByBookingsLimited(true);
        long failedPayments = transactionRepository.countByStatus("FAILED");
        long expiredKYC = 0; // standard fallback
        long servicesAwaitingApproval = serviceListingRepository.countByIsActiveFalse();
        long lowSupplyAreas = java.util.Arrays.asList("ELECTRICAL", "PLUMBING", "CLEANING", "AC", "PAINTING").stream()
                .filter(cat -> providerRepository.findEligibleProviders(cat).isEmpty())
                .count();

        summary.put("pendingRefundRequests", pendingRefundRequests);
        summary.put("pendingPayoutRequests", pendingPayoutRequests);
        summary.put("bookingsWithoutProvider", bookingsWithoutProvider);
        summary.put("emergencyBookings", emergencyBookings);
        summary.put("highPriorityComplaints", highPriorityComplaints);
        summary.put("suspendedProvidersAwaitingReview", suspendedProvidersAwaitingReview);
        summary.put("providersWithHighComplaintRate", providersWithHighComplaintRate);
        summary.put("customersWithFraudRisk", customersWithFraudRisk);
        summary.put("failedPayments", failedPayments);
        summary.put("expiredKYC", expiredKYC);
        summary.put("servicesAwaitingApproval", servicesAwaitingApproval);
        summary.put("lowSupplyAreas", lowSupplyAreas);

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

    // -----------------------------------------------------------------------------
    // CUSTOMER MANAGEMENT
    // -----------------------------------------------------------------------------

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<User>>> getCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? org.springframework.data.domain.Sort.by(sortBy).ascending() 
                : org.springframework.data.domain.Sort.by(sortBy).descending();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(userRepository.findCustomers(search, pageable)));
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
        user.setIsDeleted(true);
        userRepository.save(user);
        logAdminAction("Soft deleted customer account: " + user.getName(), "User", user.getName(), "DELETED");
        return ResponseEntity.ok(ApiResponse.success(null, "Customer account deleted successfully"));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<User>> getCustomer(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/customers/{id}/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getCustomerBookings(@PathVariable Long id) {
        List<BookingResponse> responses = taskRequestRepository.findByUserIdOrderByCreatedAtDesc(id)
                .stream()
                .map(tr -> BookingResponse.from(tr, true))
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

    @GetMapping("/customers/{id}/reviews")
    public ResponseEntity<ApiResponse<List<com.mitra.reviews.Review>>> getCustomerReviews(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(reviewRepository.findByCustomerId(id)));
    }

    @GetMapping("/customers/{id}/complaints")
    public ResponseEntity<ApiResponse<List<Complaint>>> getCustomerComplaints(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintRepository.findByCustomerIdOrderByCreatedAtDesc(id)));
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<ApiResponse<Provider>> getProvider(@PathVariable Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        return ResponseEntity.ok(ApiResponse.success(provider));
    }

    @GetMapping("/providers/{id}/bookings")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getProviderBookings(@PathVariable Long id) {
        List<BookingResponse> responses = taskRequestRepository.findAssignedTasksForProvider(id)
                .stream()
                .map(tr -> BookingResponse.from(tr, true))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/providers/{id}/complaints")
    public ResponseEntity<ApiResponse<List<Complaint>>> getProviderComplaints(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintRepository.findByProviderIdOrderByCreatedAtDesc(id)));
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

    // -----------------------------------------------------------------------------
    // PROVIDER MANAGEMENT
    // -----------------------------------------------------------------------------

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Provider>>> getProviders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? org.springframework.data.domain.Sort.by(sortBy).ascending() 
                : org.springframework.data.domain.Sort.by(sortBy).descending();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(providerRepository.findProviders(status, search, pageable)));
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
        String reason = (String) payload.get("reason");
        provider.setCommissionPercentage(rate);
        providerRepository.save(provider);
        logAdminAction("Changed commission for provider " + provider.getName(), "Provider", String.valueOf(oldRate), String.valueOf(rate), reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Provider commission updated"));
    }

    @PutMapping("/providers/{id}/assign-area")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> updateProviderArea(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
        String address = (String) payload.get("address");
        String reason = (String) payload.get("reason");
        provider.setAddress(address);
        providerRepository.save(provider);
        logAdminAction("Assigned area to provider " + provider.getName(), "Provider", "", address, reason);
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

    // -------------------------------------------------------------------------
    // PROVIDER DETAILS (PAGINATED)
    // -------------------------------------------------------------------------

    @GetMapping("/providers/{id}/metrics")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getProviderMetrics(@PathVariable Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));

        java.util.Map<String, Object> metrics = new java.util.LinkedHashMap<>();

        // Task counts
        long totalTasks = taskRequestRepository.countAssignedByProvider(id);
        long completedTasks = taskRequestRepository.countAssignedByProviderAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.COMPLETED);
        long cancelledTasks = taskRequestRepository.countAssignedByProviderAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.CANCELLED);
        long activeTasks = taskRequestRepository.countAssignedByProviderAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.STARTED);
        long pendingTasks = taskRequestRepository.countAssignedByProviderAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.ACCEPTED);

        metrics.put("totalTasks", totalTasks);
        metrics.put("completedTasks", completedTasks);
        metrics.put("cancelledTasks", cancelledTasks);
        metrics.put("activeTasks", activeTasks);
        metrics.put("pendingTasks", pendingTasks);
        metrics.put("averageRating", provider.getRatingCache());
        metrics.put("acceptanceRate", provider.getAcceptanceRate());
        metrics.put("completionRate", provider.getCompletionRate());
        metrics.put("cancellationRate", provider.getCancellationRate());
        metrics.put("responseTimeMin", provider.getResponseTimeMin());
        metrics.put("totalComplaints", complaintRepository.countByProviderId(id));

        // Earnings
        java.math.BigDecimal lifetimeEarnings = transactionRepository.sumProviderEarnings(id);
        java.time.LocalDateTime startOfMonth = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        java.math.BigDecimal monthlyEarnings = transactionRepository.sumProviderEarningsSince(id, startOfMonth);
        java.math.BigDecimal totalCommission = transactionRepository.sumProviderCommission(id);

        metrics.put("lifetimeEarnings", lifetimeEarnings);
        metrics.put("monthlyEarnings", monthlyEarnings);
        metrics.put("totalCommission", totalCommission);
        metrics.put("commissionPercentage", provider.getCommissionPercentage());

        // Wallet
        var walletOpt = providerWalletRepository.findByProviderId(id);
        metrics.put("walletBalance", walletOpt.map(w -> w.getBalance()).orElse(java.math.BigDecimal.ZERO));
        metrics.put("onlineEarnings", walletOpt.map(w -> w.getOnlineEarnings()).orElse(java.math.BigDecimal.ZERO));
        metrics.put("codEarnings", walletOpt.map(w -> w.getCodEarnings()).orElse(java.math.BigDecimal.ZERO));
        metrics.put("outstandingCommission", walletOpt.map(w -> w.getOutstandingCommission()).orElse(java.math.BigDecimal.ZERO));

        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    @GetMapping("/providers/{id}/bookings-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.bookings.dto.BookingResponse>>> getProviderBookingsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        var tasks = taskRequestRepository.findAssignedTasksForProviderPaged(id, pageable);
        var responses = tasks.map(t -> com.mitra.bookings.dto.BookingResponse.from(t, true));
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/providers/{id}/earnings-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.bookings.Transaction>>> getProviderEarningsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(transactionRepository.findByProviderIdOrderByCreatedAtDesc(id, pageable)));
    }

    @GetMapping("/providers/{id}/reviews-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.reviews.Review>>> getProviderReviewsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(reviewRepository.findByProviderIdOrderByCreatedAtDesc(id, pageable)));
    }

    @GetMapping("/providers/{id}/complaints-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.common.Complaint>>> getProviderComplaintsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(complaintRepository.findByProviderIdOrderByCreatedAtDesc(id, pageable)));
    }

    // -------------------------------------------------------------------------
    // CUSTOMER DETAILS (PAGINATED)
    // -------------------------------------------------------------------------

    @GetMapping("/customers/{id}/metrics")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getCustomerMetrics(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));

        java.util.Map<String, Object> metrics = new java.util.LinkedHashMap<>();

        long totalBookings = taskRequestRepository.countByUserId(id);
        long completedBookings = taskRequestRepository.countByUserIdAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.COMPLETED);
        long cancelledBookings = taskRequestRepository.countByUserIdAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.CANCELLED);
        long pendingBookings = taskRequestRepository.countByUserIdAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.OPEN)
                + taskRequestRepository.countByUserIdAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.QUOTING);
        long activeBookings = taskRequestRepository.countByUserIdAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.STARTED)
                + taskRequestRepository.countByUserIdAndStatus(id, com.mitra.taskrequests.TaskRequestStatus.ACCEPTED);

        metrics.put("totalBookings", totalBookings);
        metrics.put("completedBookings", completedBookings);
        metrics.put("cancelledBookings", cancelledBookings);
        metrics.put("pendingBookings", pendingBookings);
        metrics.put("activeBookings", activeBookings);
        metrics.put("totalComplaints", complaintRepository.countByCustomerId(id));
        metrics.put("rewardPoints", user.getRewardPoints());

        java.math.BigDecimal lifetimeSpend = transactionRepository.sumCustomerSpend(id);
        java.math.BigDecimal refundAmount = transactionRepository.sumCustomerRefunds(id);
        metrics.put("lifetimeSpend", lifetimeSpend);
        metrics.put("refundAmount", refundAmount);

        if (totalBookings > 0) {
            metrics.put("averageSpend", lifetimeSpend.divide(java.math.BigDecimal.valueOf(totalBookings), 2, java.math.RoundingMode.HALF_UP));
        } else {
            metrics.put("averageSpend", java.math.BigDecimal.ZERO);
        }

        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    @GetMapping("/customers/{id}/bookings-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.bookings.dto.BookingResponse>>> getCustomerBookingsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        var tasks = taskRequestRepository.findByUserIdOrderByCreatedAtDesc(id, pageable);
        var responses = tasks.map(t -> com.mitra.bookings.dto.BookingResponse.from(t, true));
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @GetMapping("/customers/{id}/reviews-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.reviews.Review>>> getCustomerReviewsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(reviewRepository.findByCustomerIdOrderByCreatedAtDesc(id, pageable)));
    }

    @GetMapping("/customers/{id}/complaints-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.common.Complaint>>> getCustomerComplaintsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(complaintRepository.findByCustomerIdOrderByCreatedAtDesc(id, pageable)));
    }

    @GetMapping("/customers/{id}/transactions-paged")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.bookings.Transaction>>> getCustomerTransactionsPaged(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(transactionRepository.findByCustomerIdOrderByCreatedAtDesc(id, pageable)));
    }

    // -----------------------------------------------------------------------------
    // BOOKING MANAGEMENT
    // -----------------------------------------------------------------------------

    @GetMapping("/bookings/{id}")
    public ResponseEntity<ApiResponse<com.mitra.bookings.dto.BookingResponse>> getBooking(@PathVariable Long id) {
        var taskRequest = taskRequestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        var response = com.mitra.bookings.dto.BookingResponse.from(taskRequest, true);
        transactionRepository.findByBookingId(id).ifPresent(tx -> {
            response.setCommissionStatus(tx.getCommissionStatus());
            response.setCommissionDueDate(tx.getCommissionDueDate());
            response.setSettlementStatus(tx.getSettlementStatus());
        });
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? org.springframework.data.domain.Sort.by(sortBy).ascending() 
                : org.springframework.data.domain.Sort.by(sortBy).descending();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);

        com.mitra.taskrequests.TaskRequestStatus taskStatus = null;
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("ALL")) {
            try {
                if (status.equalsIgnoreCase("PENDING") || status.equalsIgnoreCase("ASSIGNED")) {
                    taskStatus = com.mitra.taskrequests.TaskRequestStatus.QUOTING;
                } else {
                    taskStatus = com.mitra.taskrequests.TaskRequestStatus.valueOf(status.toUpperCase());
                }
            } catch (Exception e) {}
        }
        
        Page<com.mitra.taskrequests.TaskRequest> tasks = taskRequestRepository.findTasks(taskStatus, search, pageable);
        Page<BookingResponse> responses = tasks.map(t -> BookingResponse.from(t, true));
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
    public ResponseEntity<ApiResponse<Void>> refundBooking(
            @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));

        String refundType = (String) payload.getOrDefault("refundType", "FULL");
        String refundDestination = (String) payload.getOrDefault("refundDestination", "GATEWAY");
        BigDecimal refundAmount = payload.containsKey("amount") ? new BigDecimal(payload.get("amount").toString()) : booking.getAmountNpr();
        String reason = (String) payload.get("reason");

        // Save refund parameters in transaction if transaction exists
        List<Transaction> transactions = transactionRepository.findByProviderIdOrderByCreatedAtDesc(booking.getProvider() != null ? booking.getProvider().getId() : 0L);
        Transaction transaction = transactions.stream()
                .filter(t -> t.getBookingId().equals(id))
                .findFirst().orElse(null);

        if (transaction != null) {
            transaction.setStatus("REFUNDED");
            transaction.setRefundType(refundType);
            transaction.setRefundDestination(refundDestination);
            transactionRepository.save(transaction);

            // If WALLET destination, deduct from provider earnings wallet
            if ("WALLET".equals(refundDestination) && booking.getProvider() != null) {
                ProviderWallet wallet = providerWalletRepository.findByProviderId(booking.getProvider().getId())
                        .orElseGet(() -> providerWalletRepository.save(ProviderWallet.builder()
                                .providerId(booking.getProvider().getId())
                                .balance(BigDecimal.ZERO)
                                .currency("NPR")
                                .updatedAt(LocalDateTime.now())
                                .build()));
                BigDecimal oldBalance = wallet.getBalance();
                BigDecimal newBalance = oldBalance.subtract(refundAmount);
                wallet.setBalance(newBalance);
                providerWalletRepository.save(wallet);

                walletTransactionRepository.save(WalletTransaction.builder()
                        .walletId(wallet.getId())
                        .amount(refundAmount.negate())
                        .type("REFUND")
                        .description("Deduction due to client refund for Booking #" + id)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        logAdminAction("Issued " + refundType + " refund of " + refundAmount + " via " + refundDestination + " for booking #" + id, "Booking", "PAID", "REFUNDED", reason);
        logBookingStatusHistory(id, String.valueOf(booking.getStatus()), "Refund (" + refundType + ") processed to " + refundDestination + ". Reason: " + reason);
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

    // -----------------------------------------------------------------------------
    // SERVICE MANAGEMENT
    // -----------------------------------------------------------------------------

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
                .imageUrl(request.getImageUrl())
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
        if (request.getImageUrl() != null) service.setImageUrl(request.getImageUrl());
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

    // -----------------------------------------------------------------------------
    // PAYMENTS & PAYOUTS
    // -----------------------------------------------------------------------------

    @GetMapping("/payments/transactions")
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactions() {
        return ResponseEntity.ok(ApiResponse.success(transactionRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/payments/payouts")
    public ResponseEntity<ApiResponse<List<PayoutRequest>>> getPayoutRequests() {
        return ResponseEntity.ok(ApiResponse.success(payoutRequestRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/payments/payouts/{id}")
    public ResponseEntity<ApiResponse<PayoutRequest>> getPayoutRequest(@PathVariable Long id) {
        PayoutRequest req = payoutRequestRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PayoutRequest", id));
        return ResponseEntity.ok(ApiResponse.success(req));
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

    // -----------------------------------------------------------------------------
    // COMPLAINTS
    // -----------------------------------------------------------------------------

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<Complaint>>> getComplaints() {
        return ResponseEntity.ok(ApiResponse.success(complaintRepository.findAllByOrderByCreatedAtDesc()));
    }

    @GetMapping("/complaints/{id}")
    public ResponseEntity<ApiResponse<Complaint>> getComplaint(@PathVariable Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));
        return ResponseEntity.ok(ApiResponse.success(complaint));
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
    public ResponseEntity<ApiResponse<Void>> resolveComplaint(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));

        String remarks = (String) body.get("resolutionRemarks");
        BigDecimal penaltyAmount = body.containsKey("penaltyAmount") ? new BigDecimal(body.get("penaltyAmount").toString()) : BigDecimal.ZERO;

        complaint.setStatus("RESOLVED");
        complaint.setResolvedAt(LocalDateTime.now());
        complaint.setResolutionRemarks(remarks);

        complaintRepository.save(complaint);

        // If penaltyAmount > 0, debit the provider's wallet
        if (penaltyAmount != null && penaltyAmount.compareTo(BigDecimal.ZERO) > 0 && complaint.getProviderId() != null) {
            ProviderWallet wallet = providerWalletRepository.findByProviderId(complaint.getProviderId())
                    .orElseGet(() -> providerWalletRepository.save(ProviderWallet.builder()
                            .providerId(complaint.getProviderId())
                            .balance(BigDecimal.ZERO)
                            .currency("NPR")
                            .updatedAt(LocalDateTime.now())
                            .build()));
            BigDecimal oldBalance = wallet.getBalance();
            BigDecimal newBalance = oldBalance.subtract(penaltyAmount);
            wallet.setBalance(newBalance);
            providerWalletRepository.save(wallet);

            walletTransactionRepository.save(WalletTransaction.builder()
                    .walletId(wallet.getId())
                    .amount(penaltyAmount.negate())
                    .type("PENALTY")
                    .description("Penalty fee due to resolved Dispute Case #" + id)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        logAdminAction("Resolved complaint #" + id + ". Remarks: " + remarks + ", Penalty: " + penaltyAmount, "Complaint", complaint.getStatus(), "RESOLVED", remarks);
        return ResponseEntity.ok(ApiResponse.success(null, "Complaint resolved successfully"));
    }

    @PutMapping("/complaints/{id}/reject")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> rejectComplaint(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));

        String remarks = (String) body.get("resolutionRemarks");

        complaint.setStatus("REJECTED");
        complaint.setResolvedAt(LocalDateTime.now());
        complaint.setResolutionRemarks(remarks);

        complaintRepository.save(complaint);

        logAdminAction("Rejected complaint #" + id + ". Remarks: " + remarks, "Complaint", complaint.getStatus(), "REJECTED", remarks);
        return ResponseEntity.ok(ApiResponse.success(null, "Complaint rejected successfully"));
    }

    @PutMapping("/complaints/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> updateComplaintStatus(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));

        String status = body.get("status");
        if (status == null || (!status.equals("UNDER_REVIEW") && !status.equals("REQUEST_MORE_INFORMATION"))) {
            throw new BadRequestException("Invalid status update value. Choose UNDER_REVIEW or REQUEST_MORE_INFORMATION.");
        }

        complaint.setStatus(status);
        complaintRepository.save(complaint);

        logAdminAction("Updated complaint status for #" + id + " to " + status, "Complaint", "", status);
        return ResponseEntity.ok(ApiResponse.success(null, "Status updated to " + status));
    }

    @GetMapping("/complaints/{id}/notes")
    public ResponseEntity<ApiResponse<List<ComplaintAdminNote>>> getComplaintNotes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintAdminNoteRepository.findByComplaintIdOrderByCreatedAtDesc(id)));
    }

    @PostMapping("/complaints/{id}/notes")
    @Transactional
    public ResponseEntity<ApiResponse<ComplaintAdminNote>> addComplaintNote(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        
        String noteText = body.get("noteText");
        if (noteText == null || noteText.trim().isEmpty()) {
            throw new BadRequestException("Note content cannot be empty.");
        }

        ComplaintAdminNote note = ComplaintAdminNote.builder()
                .complaintId(id)
                .adminName(getAdminIdentity())
                .noteText(noteText.trim())
                .build();

        note = complaintAdminNoteRepository.save(note);
        logAdminAction("Added internal note to complaint #" + id, "Complaint", "", noteText);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(note));
    }

    @PutMapping("/complaints/{id}/refund")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> approveComplaintRefund(@PathVariable Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));
        
        com.mitra.taskrequests.TaskRequest task = taskRequestRepository.findById(complaint.getBookingId())
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", complaint.getBookingId()));

        if (!"ONLINE".equalsIgnoreCase(task.getPaymentMethod())) {
            throw new BadRequestException("Refund is only allowed if the booking was paid online.");
        }

        task.setPaymentStatus("REFUNDED");
        taskRequestRepository.save(task);

        transactionRepository.findByBookingId(task.getId()).ifPresent(tx -> {
            tx.setStatus("REFUNDED");
            transactionRepository.save(tx);
        });

        complaint.setStatus("RESOLVED");
        complaint.setResolvedAt(LocalDateTime.now());
        complaint.setResolutionRemarks("Refund approved of amount: ₹" + task.getFinalAmountNpr());
        complaintRepository.save(complaint);

        logAdminAction("Approved refund for complaint #" + id + ", booking #" + task.getId(), "Complaint", "", "REFUNDED");
        return ResponseEntity.ok(ApiResponse.success(null, "Refund approved and complaint resolved."));
    }

    // -----------------------------------------------------------------------------
    // REVIEWS MODERATION
    // -----------------------------------------------------------------------------

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.mitra.reviews.Review>>> getReviews(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        if (search == null || search.trim().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(reviewRepository.findAll(pageable)));
        }
        return ResponseEntity.ok(ApiResponse.success(reviewRepository.findReviews(search.trim(), pageable)));
    }

    @PutMapping("/reviews/{id}/hide")
    @Transactional
    public ResponseEntity<ApiResponse<com.mitra.reviews.Review>> toggleHideReview(@PathVariable Long id) {
        com.mitra.reviews.Review review = reviewRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", id));
        boolean oldVal = review.getIsHidden();
        review.setIsHidden(!oldVal);
        reviewRepository.save(review);
        logAdminAction("Toggled review visibility for review #" + id, "Review", String.valueOf(oldVal), String.valueOf(!oldVal));
        return ResponseEntity.ok(ApiResponse.success(review));
    }

    @DeleteMapping("/reviews/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable Long id) {
        com.mitra.reviews.Review review = reviewRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", id));
        reviewRepository.delete(review);
        logAdminAction("Permanently deleted spam review #" + id, "Review", review.getComment(), "DELETED");
        return ResponseEntity.ok(ApiResponse.success(null, "Review deleted successfully"));
    }

    // -----------------------------------------------------------------------------
    // BROADCAST NOTIFICATIONS
    // -----------------------------------------------------------------------------

    @PostMapping("/notifications/broadcast")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> sendBroadcast(@RequestBody Map<String, String> payload) {
        String target = payload.get("target"); // ALL_CUSTOMERS, ALL_PROVIDERS, SELECTED_USERS
        String channel = payload.get("channel"); // PUSH, EMAIL, SMS
        if (channel == null) channel = payload.getOrDefault("type", "PUSH");
        String title = payload.get("title");
        String content = payload.get("content");
        String reason = payload.get("reason");
        
        BroadcastNotification notification = BroadcastNotification.builder()
                .targetGroup(target != null ? target : "ALL")
                .channel(channel)
                .title(title)
                .content(content != null ? content : "")
                .sentAt(LocalDateTime.now())
                .sentBy(getAdminIdentity())
                .build();
        broadcastNotificationRepository.save(notification);

        log.info("Mock broadcast sent to: {} using {}. Content: {}", target, channel, content);
        logAdminAction("Sent broadcast notification to: " + target, "Notification", "", content, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Notification broadcast sent successfully"));
    }

    // -----------------------------------------------------------------------------
    // PLATFORM SETTINGS
    // -----------------------------------------------------------------------------

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

        // Loyalty config fields
        if (payload.containsKey("pointsPerNprSpent")) {
            settings.setPointsPerNprSpent(new BigDecimal(payload.get("pointsPerNprSpent").toString()));
        }
        if (payload.containsKey("pointsRedemptionRate")) {
            settings.setPointsRedemptionRate(new BigDecimal(payload.get("pointsRedemptionRate").toString()));
        }
        if (payload.containsKey("firstBookingPointsBonus")) {
            settings.setFirstBookingPointsBonus(Integer.valueOf(payload.get("firstBookingPointsBonus").toString()));
        }
        if (payload.containsKey("referralPointsBonus")) {
            settings.setReferralPointsBonus(Integer.valueOf(payload.get("referralPointsBonus").toString()));
        }

        platformSettingsRepository.save(settings);
        logAdminAction("Updated global platform configurations", "PlatformSettings", "", "UPDATED");
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    // -----------------------------------------------------------------------------
    // AUDIT LOGS
    // -----------------------------------------------------------------------------

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success(auditLogRepository.findAllByOrderByTimestampDesc()));
    }

    // -----------------------------------------------------------------------------
    // OPERATIONS CONTROL & HEALTH
    // -----------------------------------------------------------------------------

    @GetMapping("/analytics/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        long bookingsToday = bookingRepository.countBookingsCreatedAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0))
                + taskRequestRepository.countTasksCreatedAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0));
        BigDecimal revenueToday = transactionRepository.sumCommissionSince(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0));
        long pendingComplaints = complaintRepository.countByStatus("PENDING");
        long pendingPayouts = payoutRequestRepository.countByStatus("PENDING");
        long pendingRefunds = complaintRepository.countByCategoryAndStatus("REFUND_ISSUE", "PENDING")
                + transactionRepository.countByStatus("PENDING_REFUND");
        long pendingVerification = providerRepository.countByStatus("PENDING_REVIEW");
        long providersOnline = providerRepository.countByIsOnline(true);
        long jobsWaiting = bookingRepository.countByProviderIsNullAndStatus(BookingStatus.PENDING_DISPATCH);

        // Low supply categories
        List<String> lowSupplyCategories = new ArrayList<>();
        for (String cat : java.util.Arrays.asList("ELECTRICAL", "PLUMBING", "CLEANING", "AC", "PAINTING")) {
            if (providerRepository.findEligibleProviders(cat).isEmpty()) {
                lowSupplyCategories.add(cat);
            }
        }

        List<String> highCancellationAreas = new ArrayList<>(); // fallback list

        Map<String, Object> health = new HashMap<>();
        health.put("bookingsToday", bookingsToday);
        health.put("revenueToday", revenueToday);
        health.put("pendingComplaints", pendingComplaints);
        health.put("pendingPayouts", pendingPayouts);
        health.put("pendingRefunds", pendingRefunds);
        health.put("pendingVerification", pendingVerification);
        health.put("providersOnline", providersOnline);
        health.put("jobsWaiting", jobsWaiting);
        health.put("lowSupplyAreas", lowSupplyCategories);
        health.put("highCancellationAreas", highCancellationAreas);

        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/providers/{id}/strikes")
    public ResponseEntity<ApiResponse<List<ProviderStrike>>> getProviderStrikes(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(providerStrikeRepository.findByProviderIdOrderByCreatedAtDesc(id)));
    }

    @PostMapping("/providers/{id}/strikes")
    @Transactional
    public ResponseEntity<ApiResponse<ProviderStrike>> addProviderStrike(
            @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));

        String reason = (String) payload.get("reason");
        String notes = (String) payload.get("notes");
        String createdBy = getAdminIdentity();

        long existingCount = providerStrikeRepository.countByProviderId(id);
        int nextStrikeNum = (int) (existingCount + 1);

        ProviderStrike strike = ProviderStrike.builder()
                .providerId(id)
                .strikeNumber(nextStrikeNum)
                .reason(reason != null ? reason : "Rule violation")
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30)) // Default 30 days expiry
                .internalNotes(notes)
                .build();

        providerStrikeRepository.save(strike);

        String oldStatus = provider.getStatus();
        String newStatus = oldStatus;
        if (nextStrikeNum == 2 || nextStrikeNum == 3) {
            newStatus = "SUSPENDED";
            provider.setStatus(newStatus);
            providerRepository.save(provider);
        } else if (nextStrikeNum >= 4) {
            newStatus = "BLACK_LISTED";
            provider.setStatus(newStatus);
            providerRepository.save(provider);
        }

        logAdminAction("Issued Strike #" + nextStrikeNum + " to provider " + provider.getName(), "Provider", oldStatus, newStatus, reason);
        return ResponseEntity.ok(ApiResponse.success(strike, "Strike issued successfully"));
    }

    @PutMapping("/providers/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> updateProviderStatus(
            @PathVariable Long id, @RequestBody Map<String, String> payload) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));

        String newStatus = payload.get("status");
        String reason = payload.get("reason");
        if (newStatus == null || newStatus.isBlank()) {
            throw new BadRequestException("Status is required");
        }

        String dbStatus = newStatus;
        if ("VERIFIED".equals(newStatus)) {
            dbStatus = "APPROVED";
        } else if ("BLACKLISTED".equals(newStatus)) {
            dbStatus = "BLACK_LISTED";
        }

        String oldStatus = provider.getStatus();
        provider.setStatus(dbStatus);
        providerRepository.save(provider);

        logAdminAction("Updated provider status to " + dbStatus, "Provider", oldStatus, dbStatus, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Provider status updated successfully"));
    }

    @PutMapping("/customers/{id}/permissions")
    @Transactional
    public ResponseEntity<ApiResponse<User>> updateCustomerPermissions(
            @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));

        String reason = (String) payload.get("reason");
        if (payload.containsKey("couponsDisabled")) {
            user.setCouponsDisabled((Boolean) payload.get("couponsDisabled"));
        }
        if (payload.containsKey("rewardsDisabled")) {
            user.setRewardsDisabled((Boolean) payload.get("rewardsDisabled"));
        }
        if (payload.containsKey("bookingsLimited")) {
            user.setBookingsLimited((Boolean) payload.get("bookingsLimited"));
        }

        userRepository.save(user);
        logAdminAction("Updated customer trust flags for " + user.getName(), "User", "", "", reason);
        return ResponseEntity.ok(ApiResponse.success(user, "Customer permissions updated successfully"));
    }

    @PutMapping("/bookings/{id}/pause")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> pauseBooking(
            @PathVariable Long id, @RequestBody Map<String, String> payload) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));

        String reason = payload.get("reason");
        booking.setIsPaused(true);
        bookingRepository.save(booking);

        logAdminAction("Paused booking #" + id, "Booking", "ACTIVE", "PAUSED", reason);
        logBookingStatusHistory(id, "PAUSED", "Booking paused: " + reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking paused successfully"));
    }

    @PutMapping("/bookings/{id}/resume")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> resumeBooking(
            @PathVariable Long id, @RequestBody Map<String, String> payload) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));

        String reason = payload.get("reason");
        booking.setIsPaused(false);
        bookingRepository.save(booking);

        logAdminAction("Resumed booking #" + id, "Booking", "PAUSED", "ACTIVE", reason);
        logBookingStatusHistory(id, "RESUMED", "Booking resumed: " + reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking resumed successfully"));
    }

    @PutMapping("/bookings/{id}/escalate")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> escalateBooking(
            @PathVariable Long id, @RequestBody Map<String, String> payload) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));

        String reason = payload.get("reason");
        booking.setIsEscalated(true);
        bookingRepository.save(booking);

        logAdminAction("Escalated booking #" + id, "Booking", "NORMAL", "ESCALATED", reason);
        logBookingStatusHistory(id, "ESCALATED", "Booking escalated: " + reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking escalated successfully"));
    }

    @GetMapping("/providers/{id}/wallet")
    public ResponseEntity<ApiResponse<ProviderWallet>> getProviderWallet(@PathVariable Long id) {
        ProviderWallet wallet = providerWalletRepository.findByProviderId(id)
                .orElseGet(() -> {
                    ProviderWallet w = ProviderWallet.builder()
                            .providerId(id)
                            .balance(BigDecimal.ZERO)
                            .currency("NPR")
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return providerWalletRepository.save(w);
                });
        return ResponseEntity.ok(ApiResponse.success(wallet));
    }

    @PostMapping("/providers/{id}/wallet/adjust")
    @Transactional
    public ResponseEntity<ApiResponse<ProviderWallet>> adjustProviderWallet(
            @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        ProviderWallet wallet = providerWalletRepository.findByProviderId(id)
                .orElseGet(() -> {
                    ProviderWallet w = ProviderWallet.builder()
                            .providerId(id)
                            .balance(BigDecimal.ZERO)
                            .currency("NPR")
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return providerWalletRepository.save(w);
                });

        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String type = (String) payload.get("type"); // CREDIT, DEBIT, REFUND, PENALTY, ADJUSTMENT
        String description = (String) payload.get("description");
        String reason = (String) payload.get("reason");

        BigDecimal oldBalance = wallet.getBalance();
        BigDecimal newBalance = oldBalance.add(amount);
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now());
        providerWalletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .amount(amount)
                .type(type != null ? type : "ADJUSTMENT")
                .description(description != null ? description : "Manual adjustment")
                .createdAt(LocalDateTime.now())
                .build();
        walletTransactionRepository.save(tx);

        logAdminAction("Adjusted provider wallet balance by " + amount, "ProviderWallet", String.valueOf(oldBalance), String.valueOf(newBalance), reason);
        return ResponseEntity.ok(ApiResponse.success(wallet, "Wallet balance adjusted successfully"));
    }

    @PutMapping("/reviews/{id}/moderate")
    @Transactional
    public ResponseEntity<ApiResponse<Review>> moderateReview(
            @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", id));

        String reason = (String) payload.get("reason");
        if (payload.containsKey("isReported")) {
            review.setIsReported((Boolean) payload.get("isReported"));
        }
        if (payload.containsKey("reportReason")) {
            review.setReportReason((String) payload.get("reportReason"));
        }
        if (payload.containsKey("appealStatus")) {
            review.setAppealStatus((String) payload.get("appealStatus")); // PENDING, APPROVED, REJECTED
        }
        if (payload.containsKey("moderationNotes")) {
            review.setModerationNotes((String) payload.get("moderationNotes"));
        }
        if (payload.containsKey("isHidden")) {
            review.setIsHidden((Boolean) payload.get("isHidden"));
        }

        reviewRepository.save(review);
        logAdminAction("Moderated review #" + id, "Review", "", "", reason);
        return ResponseEntity.ok(ApiResponse.success(review, "Review moderated successfully"));
    }

    @GetMapping("/notifications/broadcast/history")
    public ResponseEntity<ApiResponse<List<BroadcastNotification>>> getBroadcastHistory() {
        return ResponseEntity.ok(ApiResponse.success(broadcastNotificationRepository.findAllByOrderBySentAtDesc()));
    }

    // -----------------------------------------------------------------------------
    // INNER REQUEST CLASSES
    // -----------------------------------------------------------------------------

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
        private String imageUrl;
    }
}
