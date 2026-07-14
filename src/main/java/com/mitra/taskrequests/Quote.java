package com.mitra.taskrequests;

import com.mitra.users.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A Quote is submitted by a provider in response to an open TaskRequest.
 * Customers can accept directly or make a counter-offer.
 */
@Entity
@Table(
    name = "quotes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_request_id", "provider_id"}),
    indexes = {
        @Index(name = "idx_quotes_provider_id", columnList = "provider_id"),
        @Index(name = "idx_quotes_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_request_id", nullable = false)
    private TaskRequest taskRequest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    /** Price the provider is willing to do the job for (NPR). */
    @Column(name = "quoted_price_npr", nullable = false, precision = 12, scale = 2)
    private BigDecimal quotedPriceNpr;

    /** Customer's counter-offer price. Null unless customer has counter-offered. */
    @Column(name = "counter_price_npr", precision = 12, scale = 2)
    private BigDecimal counterPriceNpr;

    /**
     * Final agreed price. Set when:
     * - Customer directly accepts → equals quotedPriceNpr
     * - Provider accepts counter → equals counterPriceNpr
     */
    @Column(name = "final_price_npr", precision = 12, scale = 2)
    private BigDecimal finalPriceNpr;

    /** Provider's pitch message to the customer. */
    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuoteStatus status;

    /** Optional expiry timestamp (for future auto-expiry feature). */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
