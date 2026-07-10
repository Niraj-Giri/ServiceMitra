package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.common.ApiResponse;
import com.mitra.common.BadRequestException;
import com.mitra.users.ProviderIncentive;
import com.mitra.users.ProviderIncentiveRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incentives")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IncentiveController {

    private final AuthService authService;
    private final ProviderIncentiveRepository providerIncentiveRepository;

    @GetMapping("/provider")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProviderIncentives(HttpServletRequest request) {
        Long providerId = authService.extractUserIdFromToken(request);
        if (providerId == null) {
            throw new BadRequestException("Unauthorized access");
        }

        List<ProviderIncentive> incentives = providerIncentiveRepository.findByProviderIdOrderByCreatedAtDesc(providerId);

        BigDecimal pendingPayout = incentives.stream()
                .filter(i -> "PENDING_PAYOUT".equals(i.getStatus()))
                .map(ProviderIncentive::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEarned = incentives.stream()
                .map(ProviderIncentive::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalEarnedIncentives", totalEarned);
        summary.put("pendingPayoutIncentives", pendingPayout);
        summary.put("incentives", incentives);

        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
