package com.mitra.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByPhone(String phone);

    List<Provider> findByStatus(String status);

    List<Provider> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Finds approved, online providers matching the given service category.
     * Ordered by rating descending, then total jobs descending for assignment.
     * This is the primary query used by BookingDispatchService.
     */
    @Query("""
        SELECT p FROM Provider p
        WHERE p.status = 'APPROVED'
          AND p.isOnline = TRUE
          AND LOWER(p.serviceCategory) = LOWER(:category)
        ORDER BY p.ratingCache DESC, p.totalJobs DESC
    """)
    List<Provider> findEligibleProviders(@Param("category") String category);

    /**
     * Count providers by status — for admin dashboard analytics.
     */
    long countByStatus(String status);
}
