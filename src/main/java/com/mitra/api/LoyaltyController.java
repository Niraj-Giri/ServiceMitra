package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.common.ApiResponse;
import com.mitra.common.BadRequestException;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.common.RewardPointsHistory;
import com.mitra.common.RewardPointsHistoryRepository;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import com.mitra.config.PlatformSettings;
import com.mitra.config.PlatformSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/loyalty")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LoyaltyController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final RewardPointsHistoryRepository rewardPointsHistoryRepository;
    private final PlatformSettingsRepository platformSettingsRepository;

    @GetMapping("/points")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPointsSummary(HttpServletRequest request) {
        Long userId = authService.extractUserIdFromToken(request);
        if (userId == null) {
            throw new BadRequestException("Unauthorized access");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        List<RewardPointsHistory> history = rewardPointsHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<String, Object> summary = new HashMap<>();
        summary.put("pointsBalance", user.getRewardPoints() != null ? user.getRewardPoints() : 0);
        summary.put("history", history);

        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @PostMapping("/referral")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> submitReferral(
            HttpServletRequest request,
            @RequestBody Map<String, String> payload) {
        Long userId = authService.extractUserIdFromToken(request);
        if (userId == null) {
            throw new BadRequestException("Unauthorized access");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        String referrerPhone = payload.get("referrerPhone");
        if (referrerPhone == null || referrerPhone.isBlank()) {
            throw new BadRequestException("Referral phone number is required");
        }
        referrerPhone = referrerPhone.trim();

        if (referrerPhone.equals(user.getPhone())) {
            throw new BadRequestException("You cannot refer yourself");
        }

        // Check if user already has a referral history record to prevent duplicate entries
        List<RewardPointsHistory> history = rewardPointsHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        boolean alreadyReferred = history.stream().anyMatch(h -> "REFERRAL".equals(h.getActionType()));
        if (alreadyReferred) {
            throw new BadRequestException("You have already claimed a referral bonus");
        }

        User referrer = userRepository.findByPhone(referrerPhone)
                .orElseThrow(() -> new BadRequestException("No registered customer found with this phone number"));

        PlatformSettings settings = platformSettingsRepository.findById(1L).orElse(null);
        int referralBonus = settings != null && settings.getReferralPointsBonus() != null
                ? settings.getReferralPointsBonus()
                : 30;

        // Credit referrer
        referrer.setRewardPoints((referrer.getRewardPoints() != null ? referrer.getRewardPoints() : 0) + referralBonus);
        userRepository.save(referrer);

        RewardPointsHistory referrerHistory = RewardPointsHistory.builder()
                .userId(referrer.getId())
                .points(referralBonus)
                .actionType("REFERRAL")
                .description("Referral bonus for inviting " + user.getName() + " (" + user.getPhone() + ")")
                .createdAt(LocalDateTime.now())
                .build();
        rewardPointsHistoryRepository.save(referrerHistory);

        // Credit new user
        user.setRewardPoints((user.getRewardPoints() != null ? user.getRewardPoints() : 0) + referralBonus);
        userRepository.save(user);

        RewardPointsHistory refereeHistory = RewardPointsHistory.builder()
                .userId(user.getId())
                .points(referralBonus)
                .actionType("REFERRAL")
                .description("Referral welcome bonus for signing up via " + referrer.getName())
                .createdAt(LocalDateTime.now())
                .build();
        rewardPointsHistoryRepository.save(refereeHistory);

        return ResponseEntity.ok(ApiResponse.success(null, "Referral code applied successfully"));
    }
}
