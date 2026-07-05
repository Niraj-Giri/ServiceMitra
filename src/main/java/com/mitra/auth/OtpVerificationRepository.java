package com.mitra.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    /**
     * Gets the most recently created, unused OTP for a phone number.
     * Used during OTP verification.
     */
    @Query("""
        SELECT o FROM OtpVerification o
        WHERE o.phone = :phone
          AND o.isUsed = FALSE
        ORDER BY o.createdAt DESC
        LIMIT 1
    """)
    Optional<OtpVerification> findLatestByPhone(@Param("phone") String phone);

    /**
     * Invalidates all existing unused OTPs for a phone before creating a new one.
     * Prevents multiple active OTPs for the same phone.
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE OtpVerification o SET o.isUsed = TRUE
        WHERE o.phone = :phone AND o.isUsed = FALSE
    """)
    void invalidateExistingOtps(@Param("phone") String phone);
}
