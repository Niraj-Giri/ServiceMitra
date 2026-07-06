package com.mitra.bookings;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "commission", nullable = false)
    private BigDecimal commission;

    @Column(name = "provider_earnings", nullable = false)
    private BigDecimal providerEarnings;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, RELEASED, HELD, REFUNDED

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
