package com.mitra.reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);

    List<Rating> findByProviderIdAndIsVisibleTrueOrderByCreatedAtDesc(Long providerId);

    @Query("SELECT AVG(r.overallScore) FROM Rating r WHERE r.providerId = :providerId AND r.isVisible = TRUE")
    Optional<BigDecimal> calculateAverageRating(@Param("providerId") Long providerId);

    long countByProviderId(Long providerId);
}
