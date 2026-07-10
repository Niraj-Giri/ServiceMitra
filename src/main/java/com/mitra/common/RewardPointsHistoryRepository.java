package com.mitra.common;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RewardPointsHistoryRepository extends JpaRepository<RewardPointsHistory, Long> {
    List<RewardPointsHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
