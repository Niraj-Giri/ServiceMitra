package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.common.ApiResponse;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final AuthService authService;

    /**
     * GET /api/v1/users/me
     * Returns profile of the currently authenticated user.
     * Works for both CUSTOMER and PROVIDER roles.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMe(HttpServletRequest request) {
        Long id = authService.extractUserIdFromToken(request);
        String role = authService.extractRoleFromToken(request);

        if ("PROVIDER".equalsIgnoreCase(role)) {
            Provider provider = providerRepository.findById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
            return ResponseEntity.ok(ApiResponse.success(authService.buildProviderResponse(provider)));
        } else {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("User", id));
            return ResponseEntity.ok(ApiResponse.success(authService.buildUserResponse(user)));
        }
    }

    /**
     * PUT /api/v1/users/me
     * Updates profile for the currently authenticated user.
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMe(
            HttpServletRequest request,
            @RequestBody Map<String, String> payload) {

        Long id = authService.extractUserIdFromToken(request);
        String role = authService.extractRoleFromToken(request);

        if ("PROVIDER".equalsIgnoreCase(role)) {
            Provider provider = providerRepository.findById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));
            if (payload.containsKey("name")) provider.setBusinessName(payload.get("name"));
            if (payload.containsKey("email")) provider.setEmail(payload.get("email"));
            if (payload.containsKey("skills")) provider.setSkills(payload.get("skills"));
            if (payload.containsKey("address")) provider.setAddress(payload.get("address"));
            if (payload.containsKey("workingHoursStart")) provider.setWorkingHoursStart(payload.get("workingHoursStart"));
            if (payload.containsKey("workingHoursEnd")) provider.setWorkingHoursEnd(payload.get("workingHoursEnd"));
            provider.setUpdatedAt(LocalDateTime.now());
            providerRepository.save(provider);
            return ResponseEntity.ok(ApiResponse.success(authService.buildProviderResponse(provider)));
        } else {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("User", id));
            if (payload.containsKey("name")) user.setName(payload.get("name"));
            if (payload.containsKey("email")) user.setEmail(payload.get("email"));
            userRepository.save(user);
            return ResponseEntity.ok(ApiResponse.success(authService.buildUserResponse(user)));
        }
    }
}
