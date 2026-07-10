package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    public AuthController(AuthService authService, UserRepository userRepository, ProviderRepository providerRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> payload) {
        try {
            Long id = authService.signup(
                payload.get("name"),
                payload.get("phone"),
                payload.get("email"),
                payload.get("role"),
                payload.get("serviceCategory"),
                payload.get("referredBy")
            );
            String token;
            Object principal;
            if ("PROVIDER".equalsIgnoreCase(payload.get("role"))) {
                Provider provider = providerRepository.findById(id).orElseThrow();
                token = authService.generateToken(provider);
                principal = authService.buildProviderUserMap(provider);
            } else {
                User user = userRepository.findById(id).orElseThrow();
                token = authService.generateToken(user);
                principal = user;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", principal);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> payload) {
        try {
            authService.sendOtp(payload.get("phone"));
            return ResponseEntity.ok(Map.of("message", "OTP sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> payload) {
        try {
            Object principal = authService.verifyOtp(payload.get("phone"), payload.get("otp"), payload.get("role"));
            String token;
            Object serializedPrincipal;
            if (principal instanceof Provider) {
                token = authService.generateToken((Provider) principal);
                serializedPrincipal = authService.buildProviderUserMap((Provider) principal);
            } else {
                token = authService.generateToken((User) principal);
                serializedPrincipal = principal;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", serializedPrincipal);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @PostMapping("/provider-register")
    public ResponseEntity<?> providerRegister(@RequestBody Map<String, String> payload) {
        try {
            Long providerId = authService.providerSignup(
                payload.get("name"),
                payload.get("phone"),
                payload.get("email"),
                payload.get("category"),
                payload.get("address"),
                payload.get("profilePhotoUrl"),
                payload.get("citizenUrl"),
                payload.get("otp")
            );
            Provider provider = providerRepository.findById(providerId).orElseThrow();
            String token = authService.generateToken(provider);
            Object principal = authService.buildProviderUserMap(provider);
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("user", principal);
            response.put("message", "Provider registered successfully and is pending approval");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
