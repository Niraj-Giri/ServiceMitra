package com.mitra.api;

import com.mitra.bookings.Booking;
import com.mitra.bookings.BookingRepository;
import com.mitra.bookings.BookingStatus;
import com.mitra.common.ApiResponse;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.services.ServiceListing;
import com.mitra.services.ServiceListingRepository;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin-only controller.
 *
 * In production: protect with role-based security filter (ADMIN role required).
 * Currently relies on frontend to only call these from admin UI.
 * TODO: Add @PreAuthorize("hasRole('ADMIN')") once Spring Security roles are set up.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ServiceListingRepository serviceListingRepository;

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<Provider>>> getProviders(
            @RequestParam(defaultValue = "PENDING_REVIEW") String status) {
        List<Provider> providers = providerRepository.findByStatusOrderByCreatedAtDesc(status);
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
        if (body != null) provider.setAdminNotes(body.get("notes"));
        providerRepository.save(provider);
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
        return ResponseEntity.ok(ApiResponse.success(null, "Provider rejected"));
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
        return ResponseEntity.ok(ApiResponse.success(null, "Provider suspended"));
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
        return ResponseEntity.ok(ApiResponse.success(service));
    }

    @DeleteMapping("/services/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivateService(@PathVariable Long id) {
        ServiceListing service = serviceListingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Service", id));
        service.setIsActive(false);
        serviceListingRepository.save(service);
        return ResponseEntity.ok(ApiResponse.success(null, "Service deactivated"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BOOKING MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<Page<Booking>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Booking> bookings = status != null
                ? bookingRepository.findByStatusOrderByCreatedAtDesc(
                        BookingStatus.valueOf(status), PageRequest.of(page, size))
                : bookingRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(bookings));
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
        return ResponseEntity.ok(ApiResponse.success(null, "Provider manually assigned"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ANALYTICS
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/analytics/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        Map<String, Object> summary = Map.of(
                "totalProviders", providerRepository.count(),
                "approvedProviders", providerRepository.countByStatus("APPROVED"),
                "pendingProviderReviews", providerRepository.countByStatus("PENDING_REVIEW"),
                "totalCustomers", userRepository.count(),
                "totalBookings", bookingRepository.count(),
                "completedBookings", bookingRepository.countByStatus(BookingStatus.COMPLETED),
                "pendingBookings", bookingRepository.countByStatus(BookingStatus.PENDING_DISPATCH),
                "totalRevenue", bookingRepository.sumPlatformFees()
        );
        return ResponseEntity.ok(ApiResponse.success(summary));
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
