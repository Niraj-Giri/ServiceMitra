package com.mitra.bookings;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** FLAT | PERCENTAGE */
    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "min_booking_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minBookingAmount = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "usage_limit", nullable = false)
    @Builder.Default
    private Integer usageLimit = 0;

    @Column(name = "usage_per_customer", nullable = false)
    @Builder.Default
    private Integer usagePerCustomer = 1;

    @Column(name = "applicable_category", length = 100)
    private String applicableCategory;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();
        return now.isBefore(startDate) || now.isAfter(expiryDate);
    }
}
