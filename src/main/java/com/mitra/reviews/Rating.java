package com.mitra.reviews;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer rating for a completed booking.
 *
 * One rating per booking (enforced by UNIQUE constraint on booking_id).
 * Multi-dimensional: punctuality, quality, behavior.
 * Overall score = average of the three dimensions.
 */
@Entity
@Table(name = "reviews",
    uniqueConstraints = @UniqueConstraint(columnNames = "booking_id", name = "uk_reviews_booking_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /**
     * Each dimension rated 1-5.
     */
    @Column(name = "punctuality_score", nullable = false)
    private Integer punctualityScore;

    @Column(name = "quality_score", nullable = false)
    private Integer qualityScore;

    @Column(name = "behavior_score", nullable = false)
    private Integer behaviorScore;

    /**
     * Weighted average of the 3 scores. Stored to avoid re-calculation.
     */
    @Column(name = "overall_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_visible")
    private Boolean isVisible = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Calculates the overall score as average of the three dimensions.
     */
    public static BigDecimal calculateOverall(int punctuality, int quality, int behavior) {
        double avg = (punctuality + quality + behavior) / 3.0;
        return BigDecimal.valueOf(avg).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
