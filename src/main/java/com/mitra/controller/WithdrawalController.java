package com.mitra.controller;

import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import com.mitra.entity.WithdrawalRequest;
import com.mitra.entity.WithdrawalRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/withdrawals")
@CrossOrigin(origins = "*")
public class WithdrawalController {

    @Autowired
    private WithdrawalRequestRepository withdrawalRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @PostMapping("/request")
    public ResponseEntity<?> requestWithdrawal(@RequestBody Map<String, Object> payload) {
        Long providerId = Long.valueOf(payload.get("providerId").toString());
        Double amount = Double.valueOf(payload.get("amount").toString());

        Provider provider = providerRepository.findById(providerId).orElse(null);
        if (provider == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provider not found"));
        }

        if (provider.getWalletBalance() == null || provider.getWalletBalance() < amount) {
            return ResponseEntity.badRequest().body(Map.of("error", "Insufficient balance"));
        }

        // Deduct balance immediately
        provider.setWalletBalance(provider.getWalletBalance() - amount);
        providerRepository.save(provider);

        WithdrawalRequest request = new WithdrawalRequest();
        request.setProviderId(providerId);
        request.setAmount(amount);
        request.setStatus("PENDING");
        request.setRequestedAt(LocalDateTime.now());
        withdrawalRepository.save(request);

        return ResponseEntity.ok(request);
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<WithdrawalRequest>> getProviderWithdrawals(@PathVariable Long providerId) {
        return ResponseEntity.ok(withdrawalRepository.findByProviderIdOrderByRequestedAtDesc(providerId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<WithdrawalRequest>> getAllWithdrawals() {
        return ResponseEntity.ok(withdrawalRepository.findAllByOrderByRequestedAtDesc());
    }

    @PutMapping("/{id}/process")
    public ResponseEntity<?> processWithdrawal(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        WithdrawalRequest request = withdrawalRepository.findById(id).orElse(null);
        if (request == null) return ResponseEntity.notFound().build();

        String status = payload.get("status");
        request.setStatus(status);
        request.setProcessedAt(LocalDateTime.now());

        if ("REJECTED".equals(status)) {
            // Refund
            Provider provider = providerRepository.findById(request.getProviderId()).orElse(null);
            if (provider != null) {
                provider.setWalletBalance((provider.getWalletBalance() != null ? provider.getWalletBalance() : 0.0) + request.getAmount());
                providerRepository.save(provider);
            }
        }

        withdrawalRepository.save(request);
        return ResponseEntity.ok(request);
    }
}
