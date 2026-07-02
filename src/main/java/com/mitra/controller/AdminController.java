package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import com.mitra.entity.User;
import com.mitra.entity.UserRepository;
import com.mitra.entity.Booking;
import com.mitra.entity.BookingRepository;
import com.mitra.entity.Review;
import com.mitra.entity.ReviewRepository;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @GetMapping("/pending-providers")
    public ResponseEntity<?> getPendingProviders() {
        List<Provider> pending = providerRepository.findByStatus("PENDING");
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/approve-provider/{id}")
    public ResponseEntity<?> approveProvider(@PathVariable Long id) {
        Optional<Provider> provOpt = providerRepository.findById(id);
        if (provOpt.isPresent()) {
            Provider prov = provOpt.get();
            prov.setStatus("APPROVED");
            providerRepository.save(prov);
            return ResponseEntity.ok(Collections.singletonMap("status", "success"));
        }
        return ResponseEntity.badRequest().body("Provider not found");
    }

    @PostMapping("/reject-provider/{id}")
    public ResponseEntity<?> rejectProvider(@PathVariable Long id) {
        Optional<Provider> provOpt = providerRepository.findById(id);
        if (provOpt.isPresent()) {
            Provider prov = provOpt.get();
            prov.setStatus("REJECTED");
            providerRepository.save(prov);
            return ResponseEntity.ok(Collections.singletonMap("status", "success"));
        }
        return ResponseEntity.badRequest().body("Provider not found");
    }

    @GetMapping("/all-users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/all-providers")
    public ResponseEntity<?> getAllProviders() {
        List<Provider> providers = providerRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();
        for (Provider m : providers) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", m.getId());
            dto.put("name", m.getFullName());
            dto.put("business", m.getBusinessName());
            dto.put("email", m.getEmail());
            dto.put("status", m.getStatus());
            dto.put("category", m.getServiceCategory());

            List<Review> reviews = reviewRepository.findByProviderId(m.getId());
            double avgRating = 0;
            if (!reviews.isEmpty()) {
                double sum = 0;
                for (Review r : reviews) {
                    sum += r.getRating();
                }
                avgRating = sum / reviews.size();
            }
            dto.put("rating", avgRating);

            List<Booking> bookings = bookingRepository.findByProviderIdOrderByCreatedAtDesc(m.getId());
            dto.put("totalJobs", bookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count());
            double rev = bookings.stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()) && b.getServiceCharge() != null)
                .mapToDouble(Booking::getServiceCharge).sum();
            dto.put("revenue", rev);

            response.add(dto);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all-bookings")
    public ResponseEntity<?> getAllBookings() {
        return ResponseEntity.ok(bookingRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenueStats() {
        List<Booking> allBookings = bookingRepository.findAll();
        
        long totalBookings = allBookings.size();
        long completedBookings = allBookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count();
        
        double totalRevenue = allBookings.stream()
            .filter(b -> "COMPLETED".equals(b.getStatus()) && b.getTotalBill() != null)
            .mapToDouble(Booking::getTotalBill).sum();
            
        double platformCommission = allBookings.stream()
            .filter(b -> "COMPLETED".equals(b.getStatus()) && b.getPlatformFee() != null)
            .mapToDouble(Booking::getPlatformFee).sum();

        List<Provider> providers = providerRepository.findByStatus("APPROVED");
        List<Map<String, Object>> providerRevenue = new ArrayList<>();
        
        for (Provider m : providers) {
            List<Booking> mechBookings = bookingRepository.findByProviderIdOrderByCreatedAtDesc(m.getId());
            long jobsCompleted = mechBookings.stream().filter(b -> "COMPLETED".equals(b.getStatus())).count();
            
            double mEarnings = mechBookings.stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()) && b.getServiceCharge() != null)
                .mapToDouble(Booking::getServiceCharge).sum();
                
            double mCommission = mechBookings.stream()
                .filter(b -> "COMPLETED".equals(b.getStatus()) && b.getPlatformFee() != null)
                .mapToDouble(Booking::getPlatformFee).sum();
                
            Map<String, Object> mdto = new HashMap<>();
            mdto.put("providerId", m.getId());
            mdto.put("providerName", m.getFullName());
            mdto.put("businessName", m.getBusinessName());
            mdto.put("totalEarnings", mEarnings);
            mdto.put("commission", mCommission);
            mdto.put("jobsCompleted", jobsCompleted);
            providerRevenue.add(mdto);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("totalBookings", totalBookings);
        response.put("completedBookings", completedBookings);
        response.put("totalRevenue", totalRevenue);
        response.put("platformCommission", platformCommission);
        response.put("providerRevenue", providerRevenue);
        
        return ResponseEntity.ok(response);
    }
}
