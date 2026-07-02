package com.mitra.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByProviderIdOrderByRequestedAtDesc(Long providerId);
    List<WithdrawalRequest> findAllByOrderByRequestedAtDesc();
}
