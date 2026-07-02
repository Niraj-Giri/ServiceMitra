package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.mitra.entity.Booking;
import com.mitra.entity.BookingRepository;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.mitra.entity.User;
import com.mitra.entity.UserRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/bookings")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/create")
    public ResponseEntity<?> createBooking(@RequestBody Map<String, Object> request) {
        Long customerId = Long.valueOf(request.get("customerId").toString());
        Long providerId = Long.valueOf(request.get("providerId").toString());
        String problemDescription = request.get("problem").toString();
        
        // 1. Duplicate check (within last 1 hour)
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Booking> duplicates = bookingRepository.findByCustomerIdAndProviderIdAndProblemDescriptionAndCreatedAtAfter(
            customerId, providerId, problemDescription, oneHourAgo);
        if (!duplicates.isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Duplicate booking request. Please wait before requesting again."));
        }

        LocalDateTime scheduledFor = null;
        if (request.containsKey("scheduledFor") && request.get("scheduledFor") != null) {
            scheduledFor = LocalDateTime.parse(request.get("scheduledFor").toString(), DateTimeFormatter.ISO_DATE_TIME);
        }

        if (scheduledFor != null) {
            if (scheduledFor.isBefore(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Cannot book a time slot in the past."));
            }

            Provider provider = providerRepository.findById(providerId).orElse(null);
            if (provider != null) {
                // 2. Check Working Days
                String dayOfWeek = scheduledFor.getDayOfWeek().name();
                String workingDays = provider.getWorkingDays() != null ? provider.getWorkingDays() : "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY";
                if (!workingDays.contains(dayOfWeek)) {
                    return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Provider does not work on " + dayOfWeek));
                }

                // 3. Check Working Hours
                LocalTime scheduledTime = scheduledFor.toLocalTime();
                LocalTime startTime = LocalTime.parse(provider.getWorkingHoursStart() != null ? provider.getWorkingHoursStart() : "09:00");
                LocalTime endTime = LocalTime.parse(provider.getWorkingHoursEnd() != null ? provider.getWorkingHoursEnd() : "18:00");
                
                if (scheduledTime.isBefore(startTime) || scheduledTime.plusMinutes(90).isAfter(endTime)) {
                    return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Booking time is outside provider's working hours."));
                }

                // 4. Overlapping booking check
                LocalDateTime startCheck = scheduledFor.minusMinutes(89);
                LocalDateTime endCheck = scheduledFor.plusMinutes(89);
                List<Booking> overlapping = bookingRepository.findByProviderIdAndStatusInAndScheduledForBetween(
                    providerId, Arrays.asList("ACCEPTED", "STARTED"), startCheck, endCheck);
                
                if (!overlapping.isEmpty()) {
                    return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Provider is already booked for this time slot."));
                }
            }
        }

        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setProviderId(providerId);
        booking.setAddress(request.get("address").toString());
        booking.setProblemDescription(problemDescription);
        booking.setScheduledFor(scheduledFor);

        if (request.containsKey("specialInstructions") && request.get("specialInstructions") != null) {
            booking.setSpecialInstructions(request.get("specialInstructions").toString());
        }
        if (request.containsKey("problemImageUrls") && request.get("problemImageUrls") != null) {
            booking.setProblemImageUrls(request.get("problemImageUrls").toString());
        }
        if (request.containsKey("paymentMethod") && request.get("paymentMethod") != null) {
            booking.setPaymentMethod(request.get("paymentMethod").toString());
        }
        
        String otp = String.format("%04d", new Random().nextInt(10000));
        booking.setStartOtp(otp);
        
        booking = bookingRepository.save(booking);
                
        // Notify provider via WebSocket
        messagingTemplate.convertAndSend("/topic/provider/" + booking.getProviderId(), booking);
        
        return ResponseEntity.ok(Collections.singletonMap("status", "success"));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getProviderBookings(@PathVariable Long providerId) {
        List<Booking> bookings = bookingRepository.findByProviderIdOrderByCreatedAtDesc(providerId);
        bookings.forEach(b -> b.setStartOtp(null)); // Mask OTP from provider
        return ResponseEntity.ok(bookings);
    }

    @PostMapping("/{bookingId}/status")
    public ResponseEntity<?> updateBookingStatus(@PathVariable Long bookingId, @RequestBody Map<String, String> request) {
        Optional<Booking> opt = bookingRepository.findById(bookingId);
        if (opt.isPresent()) {
            Booking booking = opt.get();
            String status = request.get("status");
            String currentStatus = booking.getStatus();
            
            // Check caller permissions
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String callerPhoneOrEmail = auth != null ? auth.getName() : null;
            
            boolean isAssignedProvider = false;
            boolean isCustomer = false;
            
            if (callerPhoneOrEmail != null) {
                Provider p = providerRepository.findByEmail(callerPhoneOrEmail).orElse(null);
                if (p != null && p.getId().equals(booking.getProviderId())) {
                    isAssignedProvider = true;
                }
                
                User u = userRepository.findByPhone(callerPhoneOrEmail).orElse(null);
                if (u != null && u.getId().equals(booking.getCustomerId())) {
                    isCustomer = true;
                }
            }
            
            if (auth != null && auth.isAuthenticated() && !callerPhoneOrEmail.equals("anonymousUser")) {
                if (!isAssignedProvider && !isCustomer) {
                    return ResponseEntity.status(403).body(Collections.singletonMap("error", "Not authorized to update this booking."));
                }
                
                if (Arrays.asList("ACCEPTED", "STARTED", "COMPLETED", "REJECTED").contains(status)) {
                    if (!isAssignedProvider) {
                        return ResponseEntity.status(403).body(Collections.singletonMap("error", "Only the assigned provider can change status to " + status));
                    }
                }
                if ("CANCELLED".equals(status)) {
                    if (!isCustomer && !isAssignedProvider) {
                        return ResponseEntity.status(403).body(Collections.singletonMap("error", "Not authorized to cancel this booking"));
                    }
                }
            }

            // Valid State Transitions
            boolean validTransition = false;
            if ("REQUESTED".equals(currentStatus) && Arrays.asList("ACCEPTED", "REJECTED", "CANCELLED").contains(status)) validTransition = true;
            else if ("ACCEPTED".equals(currentStatus) && Arrays.asList("STARTED", "CANCELLED").contains(status)) validTransition = true;
            else if ("STARTED".equals(currentStatus) && "COMPLETED".equals(status)) validTransition = true;
            else if (currentStatus.equals(status)) validTransition = true; // idempotent
            
            if (!validTransition) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Invalid state transition from " + currentStatus + " to " + status));
            }

            if ("STARTED".equals(status)) {
                String providedOtp = request.get("otp");
                if (providedOtp == null || !providedOtp.equals(booking.getStartOtp())) {
                    return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Invalid or missing OTP."));
                }
            }

            booking.setStatus(status);
            
            if ("COMPLETED".equals(status) && request.containsKey("serviceCharge")) {
                double serviceCharge = Double.parseDouble(request.get("serviceCharge"));
                booking.setServiceCharge(serviceCharge);
                double platformFee = serviceCharge * 0.05;
                booking.setPlatformFee(platformFee);
                booking.setTotalBill(serviceCharge + platformFee);
                
                // Adjust wallet balance
                Provider provider = providerRepository.findById(booking.getProviderId()).orElse(null);
                if (provider != null) {
                    double currentBalance = provider.getWalletBalance() != null ? provider.getWalletBalance() : 0.0;
                    if ("ONLINE".equals(booking.getPaymentMethod())) {
                        provider.setWalletBalance(currentBalance + (serviceCharge - platformFee));
                    } else { // CASH
                        provider.setWalletBalance(currentBalance - platformFee);
                    }
                    providerRepository.save(provider);
                }
            }
            
            bookingRepository.save(booking);
            return ResponseEntity.ok(Collections.singletonMap("status", "success"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBookings() {
        return ResponseEntity.ok(bookingRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getCustomerBookings(@PathVariable Long customerId) {
        return ResponseEntity.ok(bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId));
    }
}
