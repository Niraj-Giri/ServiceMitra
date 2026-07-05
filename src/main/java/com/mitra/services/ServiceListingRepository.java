package com.mitra.services;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceListingRepository extends JpaRepository<ServiceListing, Long> {

    List<ServiceListing> findByIsActiveTrueOrderByNameAsc();

    List<ServiceListing> findByCategoryAndIsActiveTrueOrderByNameAsc(String category);

    Optional<ServiceListing> findByIdAndIsActiveTrue(Long id);

    long countByIsActiveTrue();
}
