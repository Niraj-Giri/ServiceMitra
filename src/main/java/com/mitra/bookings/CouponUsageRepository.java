package com.mitra.bookings;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {
    long countByCouponId(Long couponId);
    long countByCouponIdAndUserId(Long couponId, Long userId);
    List<CouponUsage> findByUserId(Long userId);
}
