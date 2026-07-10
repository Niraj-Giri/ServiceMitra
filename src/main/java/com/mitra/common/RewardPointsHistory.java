package com.mitra.common;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reward_points_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardPointsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "points", nullable = false)
    private Integer points;

    @Column(name = "action_type", nullable = false)
    private String actionType; // BOOKING_EARNED, BOOKING_REDEEMED, REFERRAL, FIRST_BOOKING, PROMOTION, COMPENSATION, EXPIRED

    @Column(name = "description")
    private String description;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
