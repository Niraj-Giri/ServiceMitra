package com.mitra.users;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a service provider (worker who fulfills bookings).
 *
 * Status lifecycle:
 *   PENDING_REVIEW → APPROVED (can log in and receive jobs)
 *                  → REJECTED (cannot log in)
 *   APPROVED       → SUSPENDED (temporarily blocked)
 *   SUSPENDED      → APPROVED (reactivated by admin)
 */
@Entity
@Table(
    name = "providers",
    indexes = {
        @Index(name = "idx_providers_status", columnList = "status"),
        @Index(name = "idx_providers_category", columnList = "service_category"),
        @Index(name = "idx_providers_online", columnList = "is_online")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name")
    private String businessName;

    private String name;
    private Integer age;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * PENDING_REVIEW | APPROVED | REJECTED | SUSPENDED
     * Default is PENDING_REVIEW — admin must explicitly approve.
     */
    @Column(nullable = false)
    private String status = "PENDING_REVIEW";

    @Column(unique = true)
    private String phone;

    private String email;
    private String address;

    /**
     * Maps to service categories: ELECTRICAL, PLUMBING, CLEANING, AC, PAINTING, etc.
     */
    @Column(name = "service_category")
    private String serviceCategory;

    private Double latitude;
    private Double longitude;
    private String skills;

    @Column(name = "experience_years")
    private Integer experienceYears;

    private String languages;

    @Column(name = "is_online")
    private Boolean isOnline = false;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "pan_file_url")
    private String panFileUrl;

    @Column(name = "citizen_file_url")
    private String citizenFileUrl;

    /**
     * Cached average rating. Recalculated after each new rating submission.
     * Stored as DECIMAL(3,2) → max 9.99 (but range is 0.00–5.00).
     */
    @Column(name = "rating_cache", precision = 3, scale = 2)
    private BigDecimal ratingCache = BigDecimal.ZERO;

    @Column(name = "total_jobs")
    private Integer totalJobs = 0;

    @Column(name = "working_hours_start")
    private String workingHoursStart = "09:00";

    @Column(name = "working_hours_end")
    private String workingHoursEnd = "18:00";

    @Column(name = "working_days")
    private String workingDays = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY";



    @Column(name = "bank_details")
    private String bankDetails;

    @Column(name = "certificates_urls", columnDefinition = "TEXT")
    private String certificatesUrls;

    @Column(name = "acceptance_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal acceptanceRate = BigDecimal.valueOf(100.00);

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Column(name = "admin_notes")
    private String adminNotes;

    @Column(name = "completion_rate", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal completionRate = BigDecimal.valueOf(100.00);

    @Column(name = "cancellation_rate", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal cancellationRate = BigDecimal.valueOf(0.00);

    @Column(name = "response_time_min", nullable = false)
    @Builder.Default
    private Integer responseTimeMin = 0;

    @Column(name = "gender")
    private String gender;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "pincode")
    private String pincode;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @Column(name = "emergency_contact")
    private String emergencyContact;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "aadhaar_number")
    private String aadhaarNumber;

    @Column(name = "driving_license")
    private String drivingLicense;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
