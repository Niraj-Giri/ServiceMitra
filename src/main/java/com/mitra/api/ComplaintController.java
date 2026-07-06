package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.bookings.Booking;
import com.mitra.bookings.BookingRepository;
import com.mitra.common.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ComplaintController {

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository complaintMessageRepository;
    private final BookingRepository bookingRepository;
    private final AuthService authService;

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Complaint>> createComplaint(
            HttpServletRequest request,
            @RequestBody Map<String, Object> payload) {
        
        Long userId = authService.extractUserIdFromToken(request);
        String role = authService.extractRoleFromToken(request);

        if (!"CUSTOMER".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Only customers can submit complaints.");
        }

        Long bookingId = Long.valueOf(payload.get("bookingId").toString());
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only submit complaints for your own bookings.");
        }

        Complaint complaint = Complaint.builder()
                .bookingId(bookingId)
                .customerId(userId)
                .providerId(booking.getProvider() != null ? booking.getProvider().getId() : 0L)
                .subject((String) payload.get("subject"))
                .description((String) payload.get("description"))
                .status("PENDING")
                .priority(payload.containsKey("priority") ? (String) payload.get("priority") : "MEDIUM")
                .evidenceUrl((String) payload.get("evidenceUrl"))
                .createdAt(LocalDateTime.now())
                .build();

        complaint = complaintRepository.save(complaint);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(complaint, "Complaint submitted successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Complaint>>> getMyComplaints(HttpServletRequest request) {
        Long userId = authService.extractUserIdFromToken(request);
        String role = authService.extractRoleFromToken(request);

        List<Complaint> complaints;
        if ("PROVIDER".equalsIgnoreCase(role)) {
            complaints = complaintRepository.findByProviderIdOrderByCreatedAtDesc(userId);
        } else {
            complaints = complaintRepository.findByCustomerIdOrderByCreatedAtDesc(userId);
        }
        return ResponseEntity.ok(ApiResponse.success(complaints));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<ComplaintMessage>>> getMessages(
            HttpServletRequest request,
            @PathVariable Long id) {
        
        Long userId = authService.extractUserIdFromToken(request);
        String role = authService.extractRoleFromToken(request);

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));

        if ("PROVIDER".equalsIgnoreCase(role) && !complaint.getProviderId().equals(userId)) {
            throw new ForbiddenException("Unauthorized access to this complaint thread.");
        }
        if ("CUSTOMER".equalsIgnoreCase(role) && !complaint.getCustomerId().equals(userId)) {
            throw new ForbiddenException("Unauthorized access to this complaint thread.");
        }

        List<ComplaintMessage> messages = complaintMessageRepository.findByComplaintIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @PostMapping("/{id}/messages")
    @Transactional
    public ResponseEntity<ApiResponse<ComplaintMessage>> sendMessage(
            HttpServletRequest request,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        
        Long userId = authService.extractUserIdFromToken(request);
        String role = authService.extractRoleFromToken(request);

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Complaint", id));

        if ("PROVIDER".equalsIgnoreCase(role) && !complaint.getProviderId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to send message.");
        }
        if ("CUSTOMER".equalsIgnoreCase(role) && !complaint.getCustomerId().equals(userId)) {
            throw new ForbiddenException("Unauthorized to send message.");
        }

        ComplaintMessage message = ComplaintMessage.builder()
                .complaintId(id)
                .senderId(userId)
                .senderRole(role.toUpperCase())
                .content(payload.get("content"))
                .createdAt(LocalDateTime.now())
                .build();

        message = complaintMessageRepository.save(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(message));
    }
}
