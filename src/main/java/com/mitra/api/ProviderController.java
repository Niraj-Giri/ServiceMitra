package com.mitra.api;

import com.mitra.auth.AuthService;
import com.mitra.bookings.BookingRepository;
import com.mitra.bookings.BookingStatus;
import com.mitra.common.ApiResponse;
import com.mitra.common.ResourceNotFoundException;
import com.mitra.users.Provider;
import com.mitra.users.ProviderRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProviderController {

    private final ProviderRepository providerRepository;
    private final BookingRepository bookingRepository;
    private final AuthService authService;
    private final com.mitra.reviews.RatingRepository ratingRepository;

    /**
     * GET /api/v1/providers
     * Optional filter: ?category=ELECTRICAL
     * Optional geolocation: &lat=27.7007&lng=85.3001
     * Returns online, approved providers with reviews and calculated distance.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEligibleProviders(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        
        List<Provider> providers;
        if (category != null && !category.trim().isEmpty()) {
            providers = providerRepository.findEligibleProviders(category);
        } else {
            providers = providerRepository.findByStatus("APPROVED");
        }

        List<Map<String, Object>> responseList = providers.stream().map(provider -> {
            // Find reviews
            List<com.mitra.reviews.Rating> reviews = ratingRepository.findByProviderIdAndIsVisibleTrueOrderByCreatedAtDesc(provider.getId());

            // Calculate distance if coordinates are present
            Double distanceKm = null;
            if (lat != null && lng != null && provider.getLatitude() != null && provider.getLongitude() != null) {
                distanceKm = calculateDistance(lat, lng, provider.getLatitude(), provider.getLongitude());
            }

            // Build map response
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", provider.getId());
            map.put("businessName", provider.getBusinessName());
            map.put("name", provider.getName());
            map.put("age", provider.getAge());
            map.put("description", provider.getDescription());
            map.put("phone", provider.getPhone());
            map.put("email", provider.getEmail());
            map.put("address", provider.getAddress());
            map.put("serviceCategory", provider.getServiceCategory());
            map.put("skills", provider.getSkills());
            map.put("experienceYears", provider.getExperienceYears());
            map.put("languages", provider.getLanguages());
            map.put("isOnline", provider.getIsOnline());
            map.put("profilePhotoUrl", provider.getProfilePhotoUrl());
            map.put("ratingCache", provider.getRatingCache());
            map.put("totalJobs", provider.getTotalJobs());
            map.put("workingHoursStart", provider.getWorkingHoursStart());
            map.put("workingHoursEnd", provider.getWorkingHoursEnd());
            map.put("workingDays", provider.getWorkingDays());
            map.put("distance", distanceKm);
            
            java.time.LocalDateTime lastJobDate = bookingRepository.findLatestCompletedJobDate(provider.getId());
            map.put("lastJobCompletedAt", lastJobDate != null ? lastJobDate.toString() : null);

            map.put("reviews", reviews);

            return map;
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(responseList));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    /**
     * PUT /api/v1/providers/profile
     * Provider updates their own profile (online status, working hours, skills).
     */
    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProfile(
            HttpServletRequest httpRequest,
            @RequestBody Map<String, Object> payload) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", providerId));

        if (payload.containsKey("workingHoursStart"))
            provider.setWorkingHoursStart((String) payload.get("workingHoursStart"));
        if (payload.containsKey("workingHoursEnd"))
            provider.setWorkingHoursEnd((String) payload.get("workingHoursEnd"));
        if (payload.containsKey("workingDays"))
            provider.setWorkingDays((String) payload.get("workingDays"));
        if (payload.containsKey("skills"))
            provider.setSkills((String) payload.get("skills"));
        if (payload.containsKey("isOnline"))
            provider.setIsOnline((Boolean) payload.get("isOnline"));
        if (payload.containsKey("address"))
            provider.setAddress((String) payload.get("address"));

        provider.setUpdatedAt(LocalDateTime.now());
        providerRepository.save(provider);

        return ResponseEntity.ok(ApiResponse.success(
                authService.buildProviderResponse(provider), "Profile updated"));
    }

    /**
     * GET /api/v1/providers/earnings
     * Returns the provider's wallet balance and earnings summary.
     */
    @GetMapping("/earnings")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEarnings(
            HttpServletRequest httpRequest) {

        Long providerId = authService.extractUserIdFromToken(httpRequest);
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", providerId));

        long completedJobs = bookingRepository.countByStatus(BookingStatus.COMPLETED);

        Map<String, Object> earnings = Map.of(
                "totalJobsCompleted", provider.getTotalJobs(),
                "rating", provider.getRatingCache()
        );

        return ResponseEntity.ok(ApiResponse.success(earnings));
    }

    /**
     * GET /api/v1/providers/{id} — Public provider profile.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPublicProfile(@PathVariable Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Provider", id));

        // Public profile — limited fields
        Map<String, Object> profile = Map.of(
                "id", provider.getId(),
                "name", provider.getBusinessName(),
                "serviceCategory", provider.getServiceCategory(),
                "rating", provider.getRatingCache(),
                "totalJobs", provider.getTotalJobs(),
                "experienceYears", provider.getExperienceYears() != null ? provider.getExperienceYears() : 0,
                "skills", provider.getSkills() != null ? provider.getSkills() : "",
                "profilePhotoUrl", provider.getProfilePhotoUrl()
        );

        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
