package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "providers")
@Data
public class Provider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private String email;
    private String phone;
    private String password;
    private String businessName;
    private String serviceCategory; // e.g. AUTO_MECHANIC, AC_TECHNICIAN
    private String address;
    private String panFileUrl;
    private String citizenFileUrl;
    
    // PENDING, APPROVED, REJECTED
    private String status = "PENDING";
    
    private Double latitude;
    private Double longitude;

    private String profilePhotoUrl;
    private String skills;
    private Integer experienceYears;
    private String languages;
    private Boolean isOnline = true;
    private Double walletBalance = 0.0;
    
    private String workingHoursStart = "09:00";
    private String workingHoursEnd = "18:00";
    private String workingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY";
}
