package com.mitra.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.time.LocalDateTime;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<Booking> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Booking> findAllByOrderByCreatedAtDesc();
    
    List<Booking> findByCustomerIdAndProviderIdAndProblemDescriptionAndCreatedAtAfter(
        Long customerId, Long providerId, String problemDescription, LocalDateTime createdAt);
        
    List<Booking> findByProviderIdAndStatusInAndScheduledForBetween(
        Long providerId, List<String> statuses, LocalDateTime start, LocalDateTime end);
}
