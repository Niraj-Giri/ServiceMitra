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
    long countByCategoryAndStatus(String category, String status);
    long countByPriorityAndStatus(String priority, String status);

    org.springframework.data.domain.Page<Complaint> findByProviderIdOrderByCreatedAtDesc(Long providerId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Complaint> findByCustomerIdOrderByCreatedAtDesc(Long customerId, org.springframework.data.domain.Pageable pageable);
    long countByProviderId(Long providerId);
    long countByCustomerId(Long customerId);
    long countByProviderIdAndStatus(Long providerId, String status);
    long countByCustomerIdAndStatus(Long customerId, String status);
}
