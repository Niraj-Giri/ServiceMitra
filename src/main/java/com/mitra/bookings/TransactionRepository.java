package com.mitra.bookings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
    List<Transaction> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<Transaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    java.util.Optional<Transaction> findByBookingId(Long bookingId);
    
    @Query("SELECT COALESCE(SUM(t.commission), 0) FROM Transaction t WHERE t.status != 'REFUNDED'")
    java.math.BigDecimal sumTotalCommission();

    @Query("SELECT COALESCE(SUM(t.commission), 0) FROM Transaction t WHERE t.status != 'REFUNDED' AND t.createdAt >= :startOfDay")
    java.math.BigDecimal sumCommissionSince(@Param("startOfDay") java.time.LocalDateTime startOfDay);

    org.springframework.data.domain.Page<Transaction> findByProviderIdOrderByCreatedAtDesc(Long providerId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Transaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.providerEarnings), 0) FROM Transaction t WHERE t.providerId = :providerId AND t.status != 'REFUNDED'")
    java.math.BigDecimal sumProviderEarnings(@Param("providerId") Long providerId);

    @Query("SELECT COALESCE(SUM(t.providerEarnings), 0) FROM Transaction t WHERE t.providerId = :providerId AND t.status != 'REFUNDED' AND t.createdAt >= :since")
    java.math.BigDecimal sumProviderEarningsSince(@Param("providerId") Long providerId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.commission), 0) FROM Transaction t WHERE t.providerId = :providerId AND t.status != 'REFUNDED'")
    java.math.BigDecimal sumProviderCommission(@Param("providerId") Long providerId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.customerId = :customerId AND t.status != 'REFUNDED'")
    java.math.BigDecimal sumCustomerSpend(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.customerId = :customerId AND t.status = 'REFUNDED'")
    java.math.BigDecimal sumCustomerRefunds(@Param("customerId") Long customerId);

    long countByProviderId(Long providerId);
    long countByCustomerId(Long customerId);
}
