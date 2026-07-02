package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import com.mitra.entity.Review;
import com.mitra.entity.ReviewRepository;
import java.util.*;

@RestController
@RequestMapping("/api/providers")
@CrossOrigin(origins = "*")
public class ProviderController {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c; // in km
    }

    @GetMapping("/nearby")
    public ResponseEntity<?> getNearbyProviders(
            @RequestParam(required = false, defaultValue = "28.6139") double lat,
            @RequestParam(required = false, defaultValue = "77.2090") double lng,
            @RequestParam(required = false) String category) {
        
        List<Provider> approvedProviders;
        if (category != null && !category.isEmpty() && !category.equals("ALL")) {
            approvedProviders = providerRepository.findByServiceCategoryAndStatus(category, "APPROVED");
        } else {
            approvedProviders = providerRepository.findByStatus("APPROVED");
        }
        
        List<Map<String, Object>> response = new ArrayList<>();
        for (Provider m : approvedProviders) {
            double distance = 0.0;
            if (m.getLatitude() != null && m.getLongitude() != null) {
                distance = calculateDistance(lat, lng, m.getLatitude(), m.getLongitude());
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("id", m.getId());
            dto.put("name", m.getBusinessName());
            dto.put("owner", m.getFullName());
            dto.put("email", m.getEmail());
            dto.put("category", m.getServiceCategory());
            dto.put("distance", String.format("%.1f km", distance)); 
            
            // Calculate real average rating
            List<Review> reviewsList = reviewRepository.findByProviderId(m.getId()); // Note: review repo needs update too
            double avgRating = 5.0;
            if (!reviewsList.isEmpty()) {
                double sum = 0;
                for (Review r : reviewsList) {
                    sum += r.getRating();
                }
                avgRating = sum / reviewsList.size();
            }
            
            dto.put("rating", avgRating);
            dto.put("status", "Available");
            dto.put("img", "https://images.unsplash.com/photo-1613214149922-f1809c99b414?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80");
            dto.put("services", Arrays.asList("Diagnostics", "Repair", "Maintenance"));
            
            List<Map<String, Object>> reviews = new ArrayList<>();
            for (Review r : reviewsList) {
                 Map<String, Object> rev = new HashMap<>();
                 rev.put("user", "Customer " + r.getCustomerId());
                 rev.put("text", r.getComment());
                 rev.put("rating", r.getRating());
                 reviews.add(rev);
            }
            if (reviews.isEmpty()) {
                 Map<String, Object> review1 = new HashMap<>();
                 review1.put("user", "System");
                 review1.put("text", "No reviews yet.");
                 review1.put("rating", 5);
                 reviews.add(review1);
            }
            dto.put("reviews", reviews);
            dto.put("distanceValue", distance); // for sorting
            
            response.add(dto);
        }

        // Sort by distance
        response.sort((a, b) -> Double.compare((Double) a.get("distanceValue"), (Double) b.get("distanceValue")));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProviderById(@PathVariable Long id) {
        Optional<Provider> p = providerRepository.findById(id);
        if (p.isPresent()) {
            Provider m = p.get();
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", m.getId());
            dto.put("name", m.getBusinessName());
            dto.put("owner", m.getFullName());
            dto.put("email", m.getEmail());
            dto.put("category", m.getServiceCategory());
            dto.put("phone", m.getPhone());
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<?> updateProviderProfile(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Optional<Provider> p = providerRepository.findById(id);
        if (p.isPresent()) {
            Provider m = p.get();
            if (request.containsKey("profilePhotoUrl")) m.setProfilePhotoUrl((String) request.get("profilePhotoUrl"));
            if (request.containsKey("skills")) m.setSkills((String) request.get("skills"));
            if (request.containsKey("experienceYears")) {
                Object exp = request.get("experienceYears");
                if (exp instanceof Integer) {
                    m.setExperienceYears((Integer) exp);
                } else if (exp instanceof String) {
                    m.setExperienceYears(Integer.parseInt((String) exp));
                }
            }
            if (request.containsKey("languages")) m.setLanguages((String) request.get("languages"));
            if (request.containsKey("isOnline")) {
                Object online = request.get("isOnline");
                if (online instanceof Boolean) {
                    m.setIsOnline((Boolean) online);
                } else if (online instanceof String) {
                    m.setIsOnline(Boolean.parseBoolean((String) online));
                }
            }
            if (request.containsKey("workingHoursStart")) m.setWorkingHoursStart((String) request.get("workingHoursStart"));
            if (request.containsKey("workingHoursEnd")) m.setWorkingHoursEnd((String) request.get("workingHoursEnd"));
            if (request.containsKey("workingDays")) m.setWorkingDays((String) request.get("workingDays"));
            providerRepository.save(m);
            return ResponseEntity.ok(Collections.singletonMap("status", "success"));
        }
        return ResponseEntity.notFound().build();
    }
}
