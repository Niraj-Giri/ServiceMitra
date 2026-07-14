package com.mitra.taskrequests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request body for creating a new task request (customer posting a job).
 *
 * VAL-01: Added validation constraints to prevent constraint violation exceptions
 * or null pointer exceptions in the service and database layers.
 */
@Data
public class CreateTaskRequest {
    @NotNull(message = "Service ID is required")
    private Long serviceId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Minimum budget is required")
    @Positive(message = "Budget must be greater than zero")
    private BigDecimal budgetMinNpr;

    private BigDecimal budgetMaxNpr;

    @NotBlank(message = "Address is required")
    private String address;

    private Double latitude;
    private Double longitude;

    @NotNull(message = "Preferred date is required")
    private LocalDate preferredDate;

    @NotEmpty(message = "At least one preferred time slot is required")
    private List<String> preferredSlots;

    private Integer pointsToRedeem;
}
