package com.mitra.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<Complaint> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    long countByStatus(String status);
}
