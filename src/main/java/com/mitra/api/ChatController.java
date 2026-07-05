package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.bookings.Booking;
import com.mitra.bookings.BookingRepository;
import com.mitra.bookings.BookingStatus;
import com.mitra.chat.ChatMessage;
import com.mitra.chat.ChatMessageRepository;
import com.mitra.common.ApiResponse;
import com.mitra.common.BadRequestException;
import com.mitra.common.ForbiddenException;
import com.mitra.common.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Chat controller.
 *
 * Security rules:
 * 1. Only the customer and provider of a booking can read/write chat
 * 2. Chat is locked (read-only) once booking is in terminal state
 * 3. Sender identity comes from JWT — not from request body
 */
@RestController
@RequestMapping("/api/v1/chat/bookings/{bookingId}/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final BookingRepository bookingRepository;
    private final AuthService authService;

    /**
     * GET /api/v1/chat/bookings/{bookingId}/messages
     * Returns all chat messages for a booking.
     * Both customer and provider can read.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getMessages(
            HttpServletRequest httpRequest,
            @PathVariable Long bookingId) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String role = authService.extractRoleFromToken(httpRequest);
        Booking booking = getAndVerifyAccess(bookingId, userId, role);

        List<ChatMessage> messages = chatMessageRepository.findByBookingIdOrderByCreatedAtAsc(bookingId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    /**
     * POST /api/v1/chat/bookings/{bookingId}/messages
     * Send a message. Sender identity is extracted from JWT — not request body.
     * Chat is blocked for terminal bookings (COMPLETED, CANCELLED).
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(
            HttpServletRequest httpRequest,
            @PathVariable Long bookingId,
            @Valid @RequestBody SendMessageRequest request) {

        Long userId = authService.extractUserIdFromToken(httpRequest);
        String role = authService.extractRoleFromToken(httpRequest);
        Booking booking = getAndVerifyAccess(bookingId, userId, role);

        // Block chat for terminal bookings
        if (booking.getStatus().isTerminal()) {
            throw new BadRequestException("Chat is disabled for completed or cancelled bookings.");
        }

        // Only allow chat during ACCEPTED or STARTED
        if (!booking.getStatus().isChatAllowed()) {
            throw new BadRequestException("Chat is only available once the provider has accepted the booking.");
        }

        ChatMessage message = ChatMessage.builder()
                .booking(booking)
                .senderId(userId)
                .senderRole(role.toUpperCase())
                .content(request.getContent().trim())
                .build();

        chatMessageRepository.save(message);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(message));
    }

    /**
     * Verifies that the requesting user (from JWT) is either the
     * customer or provider of this booking.
     */
    private Booking getAndVerifyAccess(Long bookingId, Long userId, String role) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", bookingId));

        boolean isCustomer = "CUSTOMER".equalsIgnoreCase(role)
                && booking.getUser() != null
                && booking.getUser().getId().equals(userId);

        boolean isProvider = "PROVIDER".equalsIgnoreCase(role)
                && booking.getProvider() != null
                && booking.getProvider().getId().equals(userId);

        if (!isCustomer && !isProvider) {
            throw ForbiddenException.notYourResource("chat");
        }

        return booking;
    }

    @Data
    public static class SendMessageRequest {
        @NotBlank(message = "Message content cannot be empty")
        @Size(max = 1000, message = "Message cannot exceed 1000 characters")
        private String content;
    }
}
