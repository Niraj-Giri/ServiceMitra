package com.mitra.users;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payout_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, RELEASED, HELD

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
