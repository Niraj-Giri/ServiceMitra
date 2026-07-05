package com.mitra.bookings;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ─── Customer queries ────────────────────────────────────────────────────────

    Page<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByProvider_IdOrderByCreatedAtDesc(Long providerId);

    // ─── Provider queries ────────────────────────────────────────────────────────

    /**
     * Active jobs for a provider — jobs that need attention (not terminal).
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.provider.id = :providerId
          AND b.status IN ('ASSIGNED', 'ACCEPTED', 'STARTED')
        ORDER BY b.scheduledAt ASC
    """)
    List<Booking> findActiveJobsByProvider(@Param("providerId") Long providerId);

    /**
     * Job history for a provider — completed and cancelled jobs, paginated.
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.provider.id = :providerId
          AND b.status IN ('COMPLETED', 'CANCELLED_BY_CUSTOMER', 'CANCELLED_BY_PROVIDER', 'CANCELLED_BY_ADMIN')
        ORDER BY b.createdAt DESC
    """)
    Page<Booking> findHistoryByProvider(@Param("providerId") Long providerId, Pageable pageable);

    // ─── Dispatch service queries ─────────────────────────────────────────────────

    /**
     * Bookings waiting for a provider to be assigned.
     */
    List<Booking> findByStatusOrderByCreatedAtAsc(BookingStatus status);

    /**
     * Bookings that were assigned but provider hasn't responded (for timeout handling).
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.status = 'ASSIGNED'
          AND b.updatedAt < :timeoutThreshold
    """)
    List<Booking> findTimedOutAssignments(@Param("timeoutThreshold") java.time.LocalDateTime timeoutThreshold);

    // ─── Admin queries ────────────────────────────────────────────────────────────

    Page<Booking> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);

    // ─── Analytics ────────────────────────────────────────────────────────────────

    long countByStatus(BookingStatus status);

    @Query("""
        SELECT COALESCE(SUM(b.platformFee), 0)
        FROM Booking b
        WHERE b.status = 'COMPLETED'
    """)
    java.math.BigDecimal sumPlatformFees();

    @Query("""
        SELECT MAX(b.completedAt)
        FROM Booking b
        WHERE b.provider.id = :providerId
          AND b.status = 'COMPLETED'
    """)
    java.time.LocalDateTime findLatestCompletedJobDate(@Param("providerId") Long providerId);
}
