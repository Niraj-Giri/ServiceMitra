package com.mitra.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import com.mitra.entity.Booking;
import com.mitra.entity.BookingRepository;
import com.mitra.entity.Provider;
import com.mitra.entity.ProviderRepository;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/slots")
@CrossOrigin(origins = "*")
public class SlotController {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<?> getAvailableSlots(@PathVariable Long providerId, @RequestParam(name = "date") String dateStr) {
        Optional<Provider> opt = providerRepository.findById(providerId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Provider provider = opt.get();
        LocalDate date = LocalDate.parse(dateStr);
        
        String dayOfWeek = date.getDayOfWeek().name();
        String workingDays = provider.getWorkingDays() != null ? provider.getWorkingDays() : "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY";
        
        if (!workingDays.contains(dayOfWeek)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        LocalTime startTime = LocalTime.parse(provider.getWorkingHoursStart() != null ? provider.getWorkingHoursStart() : "09:00");
        LocalTime endTime = LocalTime.parse(provider.getWorkingHoursEnd() != null ? provider.getWorkingHoursEnd() : "18:00");

        List<Booking> bookings = bookingRepository.findByProviderIdAndStatusInAndScheduledForBetween(
            providerId, 
            Arrays.asList("ACCEPTED", "STARTED"), 
            date.atStartOfDay(), 
            date.plusDays(1).atStartOfDay()
        );

        List<String> availableSlots = new ArrayList<>();
        LocalTime current = startTime;
        
        while (current.plusMinutes(90).isBefore(endTime) || current.plusMinutes(90).equals(endTime)) {
            LocalDateTime slotStart = LocalDateTime.of(date, current);
            LocalDateTime slotEnd = slotStart.plusMinutes(90);
            
            boolean overlap = false;
            
            if (slotStart.isBefore(LocalDateTime.now())) {
                overlap = true;
            }
            
            for (Booking b : bookings) {
                if (b.getScheduledFor() == null) continue;
                LocalDateTime bStart = b.getScheduledFor();
                LocalDateTime bEnd = bStart.plusMinutes(90);
                
                // (StartA < EndB) and (EndA > StartB) means overlap
                if (slotStart.isBefore(bEnd) && slotEnd.isAfter(bStart)) {
                    overlap = true;
                    break;
                }
            }
            
            if (!overlap) {
                availableSlots.add(slotStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            
            current = current.plusMinutes(90); // Next slot
        }

        return ResponseEntity.ok(availableSlots);
    }
}
