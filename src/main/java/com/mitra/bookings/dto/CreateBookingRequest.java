package com.mitra.bookings.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request body for POST /api/v1/bookings
 *
 * userId is NOT taken from request body — it is extracted from the JWT token
 * in the controller for security. Clients cannot spoof bookings for other users.
 */
@Data
public class CreateBookingRequest {

    // Set by controller from JWT — NOT from request body
    private Long userId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotBlank(message = "Address is required")
    @Size(min = 10, message = "Please provide a more detailed address (at least 10 characters)")
    @Size(max = 1000, message = "Address is too long")
    private String address;

    private Long addressId;   // Optional: if user selected a saved address

    @NotNull(message = "Scheduled time is required")
    @Future(message = "Scheduled time must be in the future")
    private LocalDateTime scheduledAt;

    @NotNull(message = "Provider selection is required")
    private Long providerId;

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    private String notes;     // Optional: customer's problem description
}
