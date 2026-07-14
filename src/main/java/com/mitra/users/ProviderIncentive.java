package com.mitra.users;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "provider_incentives",
    indexes = {
        @Index(name = "idx_provider_incentives_provider_id", columnList = "provider_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderIncentive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "reason", nullable = false)
    private String reason; // COMPLETED_BOOKINGS_MILESTONE, HIGH_RATING_BONUS, REFERRAL, SPECIAL_CAMPAIGN

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "PENDING_PAYOUT"; // PENDING_PAYOUT, PAID

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "payout_id")
    private Long payoutId;
}
