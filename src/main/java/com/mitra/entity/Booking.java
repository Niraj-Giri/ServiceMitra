package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long customerId; // Fixed: Changed from String to Long to match User id
    private Long providerId;
    
    private String address;
    private String problemDescription;
    
    // REQUESTED, ACCEPTED, REJECTED, COMPLETED
    private String status = "REQUESTED";
    
    private Double serviceCharge;
    private Double platformFee;
    private Double totalBill;
    private String paymentStatus = "PENDING";
    
    private LocalDateTime scheduledFor;
    private String specialInstructions;
    private String problemImageUrls;
    private String paymentMethod;
    
    private String startOtp;
    
    private LocalDateTime createdAt = LocalDateTime.now();
}
