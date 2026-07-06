package com.mitra.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {
    List<PayoutRequest> findAllByOrderByCreatedAtDesc();
    List<PayoutRequest> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<PayoutRequest> findByStatusOrderByCreatedAtDesc(String status);
}
