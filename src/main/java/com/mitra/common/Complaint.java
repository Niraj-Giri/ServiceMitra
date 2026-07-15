package com.mitra.common;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "complaints",
    indexes = {
        @Index(name = "idx_complaints_customer_id", columnList = "customer_id"),
        @Index(name = "idx_complaints_provider_id", columnList = "provider_id"),
        @Index(name = "idx_complaints_booking_id", columnList = "booking_id"),
        @Index(name = "idx_complaints_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, RESOLVED, CLOSED

    @Column(name = "priority", nullable = false)
    private String priority; // LOW, MEDIUM, HIGH

    @Column(name = "category", nullable = false)
    private String category; // LATE_ARRIVAL, POOR_WORK, etc.

    @Column(name = "winner")
    private String winner; // CUSTOMER, PROVIDER

    @Column(name = "refund_amount")
    private java.math.BigDecimal refundAmount = java.math.BigDecimal.ZERO;

    @Column(name = "penalty_amount")
    private java.math.BigDecimal penaltyAmount = java.math.BigDecimal.ZERO;

    @Column(name = "compensation_amount")
    private java.math.BigDecimal compensationAmount = java.math.BigDecimal.ZERO;

    @Column(name = "evidence_url")
    private String evidenceUrl;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
