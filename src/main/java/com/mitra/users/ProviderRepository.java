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

    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM Provider p
        WHERE (:status IS NULL OR :status = '' OR p.status = :status)
          AND (:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(p.businessName) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(p.phone) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(p.email) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    org.springframework.data.domain.Page<Provider> findProviders(
        @Param("status") String status,
        @Param("search") String search,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Count providers by status — for admin dashboard analytics.
     */
    long countByStatus(String status);

    long countByIsOnline(Boolean isOnline);
}
