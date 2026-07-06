package com.mitra.bookings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOrderByCreatedAtDesc();
    List<Transaction> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<Transaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    
    @Query("SELECT COALESCE(SUM(t.commission), 0) FROM Transaction t WHERE t.status != 'REFUNDED'")
    java.math.BigDecimal sumTotalCommission();
}
