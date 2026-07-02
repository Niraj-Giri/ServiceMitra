package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "provider_profiles")
@Data
public class ProviderProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String businessName;
    private String experience;
    
    @Column(columnDefinition = "POINT")
    private byte[] geoLocation;
    
    private Boolean isAvailable;
}
