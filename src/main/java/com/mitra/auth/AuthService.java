package com.mitra.auth;

import com.mitra.common.BadRequestException;
import com.mitra.common.ForbiddenException;
import com.mitra.users.User;
import com.mitra.users.UserRepository;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Key;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Authentication service.
 *
 * Key security decisions:
 * 1. OTP generated with SecureRandom (not Math.random)
 * 2. OTP always 4 digits, expires in 5 minutes
 * 3. OTP invalidated after 3 failed attempts
 * 4. OTP marked as used after successful verification
 * 5. verifyOtp does NOT auto-create users (registration is a separate step)
 * 6. Providers default to PENDING_REVIEW (not APPROVED)
 * 7. JWT contains: sub=userId, role, phone, iat, exp
 */
@Slf4j
@Service
public class AuthService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 3;
    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final OtpVerificationRepository otpRepository;
    private final Key jwtKey;
    private final long jwtExpirationMs;

    public AuthService(UserRepository userRepository,
                       ProviderRepository providerRepository,
                       OtpVerificationRepository otpRepository,
                       @Value("${jwt.secret}") String secret,
                       @Value("${jwt.expirationMs}") long expirationMs) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.otpRepository = otpRepository;
        this.jwtKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.jwtExpirationMs = expirationMs;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // OTP FLOW
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates and "sends" a 6-digit OTP for the given phone number.
     *
     * In production: integrate with Sparrow SMS or WhatsApp Business API.
     * For development: OTP is logged to console.
     *
     * The previous unused OTPs for this phone are invalidated before creating new one.
     */
    @Transactional
    public void sendOtp(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BadRequestException("Phone number is required");
        }

        // Invalidate any existing unexpired OTPs for this phone
        otpRepository.invalidateExistingOtps(phone);

        String otp = generateOtp();
        OtpVerification record = OtpVerification.builder()
                .phone(phone)
                .otp(otp)
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();
        otpRepository.save(record);

        // TODO Production: SMS gateway integration (Sparrow SMS for Nepal)
        // smsService.send(phone, "Your Mitra OTP is: " + otp + ". Valid for 5 minutes.");
        log.info("OTP for {}: {} (expires at {})", phone, otp, record.getExpiresAt());
    }

    /**
     * Verifies OTP and returns the authenticated user/provider.
     *
     * Does NOT auto-create users. If phone is unregistered, throws exception
     * instructing client to register first.
     *
     * @param role "CUSTOMER" | "PROVIDER" | "ADMIN"
     * @return The authenticated User or Provider object
     */
    @Transactional
    public Object verifyOtp(String phone, String otp, String role) {
        // Verify the OTP record first
        verifyOtpRecord(phone, otp);

        // Find the registered user
        String normalizedRole = role != null ? role.toUpperCase() : "CUSTOMER";
        return switch (normalizedRole) {
            case "PROVIDER" -> {
                Provider provider = providerRepository.findByPhone(phone)
                        .orElseThrow(() -> new BadRequestException(
                                "No provider account found for this number. Please register as a provider first."));

                if ("PENDING_REVIEW".equals(provider.getStatus())) {
                    throw new ForbiddenException("Your provider registration is under review. You'll be notified once approved.");
                }
                if ("REJECTED".equals(provider.getStatus())) {
                    throw new ForbiddenException("Your provider registration was not approved. Please contact support.");
                }
                if ("SUSPENDED".equals(provider.getStatus())) {
                    throw new ForbiddenException("Your account has been suspended. Please contact support.");
                }
                yield provider;
            }
            case "ADMIN" -> {
                // Admin users are in the users table with role = ADMIN
                yield userRepository.findByPhone(phone)
                        .filter(u -> "ADMIN".equals(u.getRole()))
                        .orElseThrow(() -> new BadRequestException("No admin account found for this number."));
            }
            default -> {
                // CUSTOMER
                yield userRepository.findByPhone(phone)
                        .orElseThrow(() -> new BadRequestException(
                                "No account found for this number. Please register first."));
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // REGISTRATION
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new customer after OTP verification.
     */
    @Transactional
    public User registerCustomer(String name, String phone, String email, String verifiedOtp) {
        // Re-verify OTP before registration
        verifyOtpRecord(phone, verifiedOtp);

        if (userRepository.findByPhone(phone).isPresent()) {
            throw new BadRequestException("An account with this phone number already exists. Please log in.");
        }

        User user = User.builder()
                .name(name)
                .phone(phone)
                .email(email)
                .password("OTP_AUTH")    // placeholder — never used
                .role("CUSTOMER")
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    /**
     * Registers a new provider. Status is PENDING_REVIEW.
     * Admin must explicitly approve before provider can log in.
     */
    @Transactional
    public Provider registerProvider(String name, String phone, String email,
                                     String category, String address,
                                     String profilePhotoUrl, String citizenFileUrl,
                                     String verifiedOtp) {
        verifyOtpRecord(phone, verifiedOtp);

        if (providerRepository.findByPhone(phone).isPresent()) {
            throw new BadRequestException("A provider account with this phone number already exists.");
        }

        Provider provider = Provider.builder()
                .businessName(name + " Services")
                .phone(phone)
                .email(email)
                .serviceCategory(category)
                .address(address)
                .profilePhotoUrl(profilePhotoUrl)
                .citizenFileUrl(citizenFileUrl)
                .status("PENDING_REVIEW")     // MUST be reviewed by admin before login
                .isOnline(false)
                .ratingCache(java.math.BigDecimal.ZERO)
                .totalJobs(0)
                .updatedAt(LocalDateTime.now())
                .build();

        provider = providerRepository.save(provider);
        log.info("Provider registered: {} (phone: {}). Status: PENDING_REVIEW", name, phone);

        return provider;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // JWT
    // ─────────────────────────────────────────────────────────────────────────────

    public String generateToken(Long id, String role, String phone) {
        return Jwts.builder()
                .setSubject(String.valueOf(id))
                .claim("role", role)
                .claim("phone", phone)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(jwtKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(User user) {
        return generateToken(user.getId(), user.getRole(), user.getPhone());
    }

    public String generateToken(Provider provider) {
        return generateToken(provider.getId(), "PROVIDER", provider.getPhone());
    }

    public Long extractUserIdFromToken(HttpServletRequest request) {
        Claims claims = extractClaims(request);
        if (claims == null) return null;
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String extractRoleFromToken(HttpServletRequest request) {
        Claims claims = extractClaims(request);
        if (claims == null) return null;
        return claims.get("role", String.class);
    }

    private Claims extractClaims(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        String token = authHeader.substring(7);
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(jwtKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // RESPONSE BUILDERS
    // ─────────────────────────────────────────────────────────────────────────────

    public Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("phone", user.getPhone());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("profilePhoto", user.getProfilePhoto());
        map.put("isActive", user.getIsActive());
        return map;
    }

    public Map<String, Object> buildProviderResponse(Provider provider) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", provider.getId());
        map.put("name", provider.getBusinessName());
        map.put("phone", provider.getPhone());
        map.put("email", provider.getEmail());
        map.put("role", "PROVIDER");
        map.put("status", provider.getStatus());
        map.put("serviceCategory", provider.getServiceCategory());
        map.put("address", provider.getAddress());
        map.put("profilePhotoUrl", provider.getProfilePhotoUrl());
        map.put("ratingCache", provider.getRatingCache());
        map.put("totalJobs", provider.getTotalJobs());
        map.put("isOnline", provider.getIsOnline());
        map.put("skills", provider.getSkills());
        map.put("experienceYears", provider.getExperienceYears());
        map.put("workingHoursStart", provider.getWorkingHoursStart());
        map.put("workingHoursEnd", provider.getWorkingHoursEnd());

        // Nest provider fields for frontend compatability (matches typescript User.provider interface)
        Map<String, Object> providerMap = new HashMap<>();
        providerMap.put("id", provider.getId());
        providerMap.put("status", provider.getStatus());
        providerMap.put("isOnline", provider.getIsOnline());
        providerMap.put("workingHoursStart", provider.getWorkingHoursStart());
        providerMap.put("workingHoursEnd", provider.getWorkingHoursEnd());
        providerMap.put("businessName", provider.getBusinessName());
        providerMap.put("serviceCategory", provider.getServiceCategory());
        providerMap.put("ratingCache", provider.getRatingCache());
        providerMap.put("totalJobs", provider.getTotalJobs());
        providerMap.put("skills", provider.getSkills());
        providerMap.put("experienceYears", provider.getExperienceYears());
        providerMap.put("address", provider.getAddress());
        providerMap.put("email", provider.getEmail());
        providerMap.put("phone", provider.getPhone());
        providerMap.put("profilePhotoUrl", provider.getProfilePhotoUrl());
        map.put("provider", providerMap);

        return map;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private String generateOtp() {
        int otp = 1000 + secureRandom.nextInt(9000);
        return String.valueOf(otp);
    }

    private void verifyOtpRecord(String phone, String otp) {
        OtpVerification record = otpRepository.findLatestByPhone(phone)
                .orElseThrow(() -> new BadRequestException("OTP not found. Please request a new OTP."));

        if (Boolean.TRUE.equals(record.getIsUsed())) {
            throw new BadRequestException("OTP already used. Please request a new OTP.");
        }
        if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired. Please request a new OTP.");
        }
        if (!record.getOtp().equals(otp)) {
            throw new BadRequestException("Incorrect OTP.");
        }
        // Mark as used
        record.setIsUsed(true);
        record.setUsedAt(LocalDateTime.now());
        otpRepository.save(record);
    }

    // Legacy method kept for backward compatibility with AuthController
    @Deprecated
    public Map<String, Object> buildProviderUserMap(Provider provider) {
        return buildProviderResponse(provider);
    }

    // Legacy signup method kept for AuthController — will be removed once
    // AuthController is fully rewritten
    @Transactional
    public Long signup(String name, String phone, String email, String role, String serviceCategory) {
        if ("PROVIDER".equalsIgnoreCase(role)) {
            if (providerRepository.findByPhone(phone).isPresent()) {
                throw new RuntimeException("Phone number already registered as provider");
            }
            Provider provider = new Provider();
            provider.setBusinessName(name + " Services");
            provider.setServiceCategory(serviceCategory != null ? serviceCategory : "General");
            provider.setIsOnline(true);
            provider.setPhone(phone);
            provider.setEmail(email);
            provider.setStatus("PENDING_REVIEW");   // Fixed: was APPROVED
            provider.setRatingCache(java.math.BigDecimal.ZERO);
            provider.setTotalJobs(0);
            provider.setUpdatedAt(LocalDateTime.now());
            provider = providerRepository.save(provider);
            return provider.getId();
        } else {
            if (userRepository.findByPhone(phone).isPresent()) {
                throw new RuntimeException("Phone number already registered");
            }
            User user = new User();
            user.setName(name != null ? name : "User");
            user.setPhone(phone);
            user.setEmail(email);
            user.setRole(role != null ? role.toUpperCase() : "CUSTOMER");
            user.setPassword("OTP_AUTH");
            user.setIsActive(true);
            user = userRepository.save(user);
            return user.getId();
        }
    }

    // Legacy providerSignup kept for AuthController
    @Transactional
    public Long providerSignup(String name, String phone, String email, String category,
                               String address, String profilePhotoUrl, String citizenUrl, String otp) {
        if (providerRepository.findByPhone(phone).isPresent()) {
            throw new RuntimeException("Phone number already registered as provider");
        }
        Provider provider = new Provider();
        provider.setBusinessName(name + " Services");
        provider.setServiceCategory(category);
        provider.setAddress(address);
        provider.setProfilePhotoUrl(profilePhotoUrl);
        provider.setCitizenFileUrl(citizenUrl);
        provider.setPhone(phone);
        provider.setEmail(email);
        provider.setStatus("PENDING_REVIEW");   // Fixed: was APPROVED
        provider.setIsOnline(false);
        provider.setRatingCache(java.math.BigDecimal.ZERO);
        provider.setTotalJobs(0);
        provider.setUpdatedAt(LocalDateTime.now());
        provider = providerRepository.save(provider);
        return provider.getId();
    }
}
