package com.mitra.bookings;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * SEC-09: Pessimistic write lock on coupon lookup during checkout.
     *
     * Problem: Without a lock, two concurrent checkout requests for the same
     * coupon at the usage limit both pass the "count >= limit" check before
     * either has persisted a CouponUsage record, allowing double-spend.
     *
     * Solution: PESSIMISTIC_WRITE acquires a DB-level exclusive row lock
     * at SELECT time. The second request blocks until the first transaction
     * commits or rolls back, ensuring atomicity of the check-then-save.
     *
     * Use this method in TaskRequestService.checkoutTaskRequest() instead
     * of findByCodeIgnoreCase() to protect the coupon usage boundary.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE LOWER(c.code) = LOWER(:code)")
    Optional<Coupon> findByCodeIgnoreCaseForUpdate(@Param("code") String code);
}
