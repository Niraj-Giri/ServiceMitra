package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.bookings.BookingService;
import com.mitra.bookings.dto.BookingResponse;
import com.mitra.bookings.dto.CreateBookingRequest;
import com.mitra.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Booking REST controller.
 *
 * Responsibilities:
 * 1. Parse HTTP request (extract token, validate request body)
 * 2. Delegate ALL business logic to BookingService
 * 3. Return structured ApiResponse
 *
 * This controller does NOT:
 * - Access repositories directly
 * - Contain any business logic
 * - Generate OTPs
 * - Perform status transitions
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookingController {

    private final BookingService bookingService;
    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────────────────────
    // CUSTOMER ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/bookings
     * Customer creates a new booking.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            HttpServletRequest httpRequest,
            @Valid @RequestBody CreateBookingRequest request) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        request.setUserId(userId);   // inject from token, not request body

        BookingResponse response = bookingService.createBooking(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Booking created successfully"));
    }

    /**
     * GET /api/v1/bookings/user
     * Customer's bookings list. Matches frontend apiClient call.
     */
    @GetMapping("/user")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getUserBookings(
            HttpServletRequest httpRequest) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        List<BookingResponse> bookings = bookingService.getCustomerBookingsList(userId);
        return ResponseEntity.ok(ApiResponse.success(bookings));
    }

    /**
     * GET /api/v1/bookings/{id}
     * Customer fetches a single booking (includes OTP when status = ACCEPTED).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        BookingResponse response = bookingService.getBookingForCustomer(id, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/bookings/{id}/cancel
     * Customer cancels their booking.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String reason = body != null ? body.get("reason") : null;
        bookingService.cancelByCustomer(id, userId, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking cancelled"));
    }

    /**
     * POST /api/v1/bookings/{id}/reschedule
     * Customer reschedules their booking.
     */
    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<BookingResponse>> rescheduleBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String scheduledAtStr = body.get("scheduledAt");
        if (scheduledAtStr == null || scheduledAtStr.isBlank()) {
            throw new com.mitra.common.BadRequestException("New scheduled date and time is required");
        }

        java.time.LocalDateTime newScheduledAt = java.time.LocalDateTime.parse(scheduledAtStr);
        BookingResponse response = bookingService.rescheduleBooking(id, userId, newScheduledAt);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking rescheduled successfully"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PROVIDER ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/bookings/provider
     * Provider's bookings list. Matches frontend dashboard call.
     */
    @GetMapping("/provider")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getProviderBookings(
            HttpServletRequest httpRequest) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        List<BookingResponse> jobs = bookingService.getProviderBookingsList(providerId);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    /**
     * GET /api/v1/bookings/provider/{id}
     * Provider fetches single booking detail.
     */
    @GetMapping("/provider/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getProviderBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        BookingResponse response = bookingService.getBookingForProvider(id, providerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * PUT /api/v1/bookings/{id}/accept
     * Provider accepts an assigned booking. Matches frontend.
     */
    @PutMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<BookingResponse>> acceptBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        BookingResponse response = bookingService.acceptBooking(id, providerId);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking accepted. OTP sent to customer."));
    }

    /**
     * PUT /api/v1/bookings/{id}/reject
     * Provider rejects an assigned booking. Matches frontend.
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        String reason = body != null ? body.get("reason") : null;
        bookingService.rejectBooking(id, providerId, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking rejected"));
    }

    /**
     * PUT /api/v1/bookings/{id}/start?otp=1234
     * Provider enters OTP from customer to officially start the job. Matches frontend.
     */
    @PutMapping("/{id}/start")
    public ResponseEntity<ApiResponse<BookingResponse>> startBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestParam String otp) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        BookingResponse response = bookingService.startBooking(id, providerId, otp);
        return ResponseEntity.ok(ApiResponse.success(response, "Job started successfully"));
    }

    /**
     * POST /api/v1/bookings/{id}/complete
     * Provider marks the job as complete. Triggers earning credit. Matches frontend.
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(
            HttpServletRequest httpRequest,
            @PathVariable Long id) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        BookingResponse response = bookingService.completeBooking(id, providerId);
        return ResponseEntity.ok(ApiResponse.success(response, "Job completed successfully. Earnings credited."));
    }

    /**
     * POST /api/v1/bookings/{id}/provider-cancel
     * Provider cancels a booking they had accepted.
     */
    @PostMapping("/{id}/provider-cancel")
    public ResponseEntity<ApiResponse<Void>> providerCancel(
            HttpServletRequest httpRequest,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        String reason = body != null ? body.get("reason") : null;
        bookingService.cancelByProvider(id, providerId, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Booking cancelled"));
    }
}
