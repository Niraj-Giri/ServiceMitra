package com.mitra.config;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "platform_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_name", nullable = false)
    private String platformName;

    @Column(name = "commission_percentage", nullable = false)
    private BigDecimal commissionPercentage;

    @Column(name = "support_number", nullable = false)
    private String supportNumber;

    @Column(name = "cancellation_policy", columnDefinition = "TEXT")
    private String cancellationPolicy;

    @Column(name = "auto_assignment_rules")
    private String autoAssignmentRules;

    @Column(name = "booking_radius")
    private Double bookingRadius;

    @Column(name = "working_hours")
    private String workingHours;

    @Column(name = "tax_settings")
    private String taxSettings;

    @Column(name = "payment_gateway")
    private String paymentGateway;
}
