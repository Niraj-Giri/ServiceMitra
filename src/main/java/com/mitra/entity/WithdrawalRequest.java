package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawal_requests")
@Data
public class WithdrawalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long providerId;
    private Double amount;
    
    // PENDING, PROCESSED, REJECTED
    private String status = "PENDING";
    
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
}
