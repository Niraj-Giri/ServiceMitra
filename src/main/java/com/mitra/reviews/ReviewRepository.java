package com.mitra.reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProviderId(Long providerId);
    List<Review> findByCustomerId(Long customerId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT r FROM Review r
        WHERE LOWER(r.comment) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(r.customer.name) LIKE LOWER(CONCAT('%', :search, '%'))
           OR LOWER(r.provider.businessName) LIKE LOWER(CONCAT('%', :search, '%'))
    """)
    org.springframework.data.domain.Page<Review> findReviews(
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable
    );

    long countByIsReported(Boolean isReported);
    org.springframework.data.domain.Page<Review> findByIsReported(Boolean isReported, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Review> findByProviderIdOrderByCreatedAtDesc(Long providerId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Review> findByCustomerIdOrderByCreatedAtDesc(Long customerId, org.springframework.data.domain.Pageable pageable);
}
