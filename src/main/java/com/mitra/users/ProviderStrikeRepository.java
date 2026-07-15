package com.mitra.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProviderStrikeRepository extends JpaRepository<ProviderStrike, Long> {
    List<ProviderStrike> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    long countByProviderId(Long providerId);
}
