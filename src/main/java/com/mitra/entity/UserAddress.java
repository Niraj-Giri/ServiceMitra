package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_addresses")
@Data
public class UserAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long customerId;
    
    private String title; // e.g., "Home", "Work", "Current Location"
    private String addressLine;
    
    private Double latitude;
    private Double longitude;
    
    private Boolean isDefault = false;
}
