package com.mitra.services;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @deprecated Use {@link ServiceListing} instead.
 * This class is kept temporarily for backward compatibility with
 * classes that still import com.mitra.services.Service.
 * Will be removed once all references are migrated.
 */
@Deprecated
@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "price_type")
    private String priceType = "FIXED";

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
