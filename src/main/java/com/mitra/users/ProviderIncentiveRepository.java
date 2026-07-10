package com.mitra.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProviderIncentiveRepository extends JpaRepository<ProviderIncentive, Long> {
    List<ProviderIncentive> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<ProviderIncentive> findByProviderIdAndStatus(Long providerId, String status);
    List<ProviderIncentive> findByPayoutId(Long payoutId);
}
