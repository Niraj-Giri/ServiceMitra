package com.mitra.services;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findByCategory(String category);
    List<Service> findByIsActiveTrue();
}
