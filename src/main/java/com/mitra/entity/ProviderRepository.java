package com.mitra.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    List<Provider> findByStatus(String status);
    Optional<Provider> findByEmail(String email);
    List<Provider> findByServiceCategoryAndStatus(String serviceCategory, String status);
}

