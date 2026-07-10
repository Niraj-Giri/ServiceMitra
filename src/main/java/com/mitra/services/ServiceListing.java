package com.mitra.services;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a bookable service offered on the platform.
 *
 * Renamed from 'Service' to 'ServiceListing' to avoid collision with
 * Spring's @Service annotation which caused ambiguous import issues.
 *
 * Maps to the existing 'services' table (no DB change needed).
 */
@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Category identifier. Matches Provider.serviceCategory for assignment matching.
     * Examples: ELECTRICAL, PLUMBING, CLEANING, AC, PAINTING
     */
    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String name;

    private String description;

    /**
     * Fixed price in NPR. This is what the customer pays and provider earns from.
     * Platform takes 10% on completion.
     */
    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "price_type")
    private String priceType = "FIXED";

    @Column(name = "duration_min")
    private Integer durationMin;           // estimated duration in minutes

    @Column(name = "what_included", columnDefinition = "TEXT")
    private String whatIncluded;           // JSON array or plain text

    @Column(name = "what_excluded", columnDefinition = "TEXT")
    private String whatExcluded;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    public Boolean getIsActive() {
        return isActive == null || isActive;
    }

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
