package com.mitra.bookings;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_usages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task_request_id")
    private Long taskRequestId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
