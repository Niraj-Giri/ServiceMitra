package com.mitra.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProviderWalletRepository extends JpaRepository<ProviderWallet, Long> {
    Optional<ProviderWallet> findByProviderId(Long providerId);
}
