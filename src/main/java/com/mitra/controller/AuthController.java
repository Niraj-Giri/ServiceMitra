package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.mitra.entity.User;
import com.mitra.entity.UserRepository;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import com.mitra.auth.security.JwtUtil;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/check-user")
    public ResponseEntity<?> checkUser(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        boolean exists = userRepository.findByPhone(phone).isPresent();
        return ResponseEntity.ok(Collections.singletonMap("registered", exists));
    }

    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        boolean exists = userRepository.findByEmail(email).isPresent();
        return ResponseEntity.ok(Collections.singletonMap("registered", exists));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(Collections.singletonMap("status", "sent"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String name = request.get("name");
        String email = request.get("email");
        String otp = request.get("otp");

        Optional<User> userOpt = userRepository.findByPhone(phone);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            // New registration
            if (email != null && !email.isEmpty() && userRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.status(400).body(Collections.singletonMap("error", "Email is already registered."));
            }
            user = new User();
            user.setPhone(phone);
            user.setFullName(name);
            user.setEmail(email);
            user.setRole("CUSTOMER");
        }
        user = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtUtil.generateToken(user.getPhone()));
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    // ===== PROVIDER LOGIN (email + password) =====
    @PostMapping("/provider-login")
    public ResponseEntity<?> providerLogin(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        Optional<Provider> provOpt = providerRepository.findByEmail(email);
        if (provOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "No provider found with this email"));
        }

        Provider provider = provOpt.get();
        if (!provider.getPassword().equals(password)) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Invalid password"));
        }

        if (!"APPROVED".equals(provider.getStatus())) {
            return ResponseEntity.status(403).body(Collections.singletonMap("error", "Your account is still pending admin approval"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtUtil.generateToken(provider.getEmail()));
        response.put("provider", provider);
        return ResponseEntity.ok(response);
    }

    // ===== ADMIN LOGIN (email + password) =====
    @PostMapping("/admin-login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        Optional<User> adminOpt = userRepository.findByEmailAndRole(email, "ADMIN");
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "No admin account found with this email"));
        }

        User admin = adminOpt.get();
        if (!admin.getPassword().equals(password)) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Invalid password"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtUtil.generateToken(admin.getEmail()));
        response.put("user", admin);
        return ResponseEntity.ok(response);
    }
}
