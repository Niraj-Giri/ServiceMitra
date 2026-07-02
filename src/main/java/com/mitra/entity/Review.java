package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@Data
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;
    private Long customerId;
    private Long providerId;
    
    private int rating; // 1 to 5
    private String comment;
    
    private LocalDateTime createdAt = LocalDateTime.now();
}
