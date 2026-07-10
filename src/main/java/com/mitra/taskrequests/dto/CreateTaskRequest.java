package com.mitra.taskrequests.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request body for creating a new task request (customer posting a job). */
@Data
public class CreateTaskRequest {
    private Long serviceId;
    private String title;
    private String description;
    private BigDecimal budgetMinNpr;
    private BigDecimal budgetMaxNpr;
    private String address;
    private Double latitude;
    private Double longitude;
    private LocalDate preferredDate;
    private Integer pointsToRedeem;
}
