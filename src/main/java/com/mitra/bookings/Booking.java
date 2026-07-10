package com.mitra.bookings;

import com.mitra.users.Provider;
import com.mitra.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core booking entity. Represents a customer's service request from creation to completion.
 *
 * Status lifecycle: see BookingStatus enum.
 *
 * Key design decisions:
 * - service_name is a snapshot of the service name at booking time (denormalized intentionally).
 *   If a service is renamed later, historical bookings still show the original name.
 * - address_text is a snapshot of the address at booking time for the same reason.
 * - rejected_provider_ids stores comma-separated provider IDs who rejected this booking.
 *   This is used by BookingDispatchService to skip already-rejected providers.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /**
     * Snapshot of service name at booking time. Never changes even if admin renames service.
     */
    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "address_id")
    private Long addressId;

    /**
     * Snapshot of the full address text at booking time.
     */
    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    private String notes;

    /**
     * Base service amount (NPR). Set at booking creation from service.basePrice.
     */
    @Column(name = "amount_npr", precision = 12, scale = 2)
    private BigDecimal amountNpr;

    /**
     * Platform commission (10% of amountNpr). Set on COMPLETED.
     */
    @Column(name = "platform_fee", precision = 12, scale = 2)
    private BigDecimal platformFee;

    /**
     * Total bill for the customer (= amountNpr for MVP, may include extras later).
     * Set on COMPLETED.
     */
    @Column(name = "total_bill", precision = 12, scale = 2)
    private BigDecimal totalBill;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "service_charge", precision = 12, scale = 2)
    private BigDecimal serviceCharge;

    /**
     * 6-digit OTP generated when provider accepts. Customer shows this to provider to start job.
     * Cleared after use.
     */
    @Column(name = "start_otp", length = 6)
    private String startOtp;

    /**
     * When the OTP was generated. Used to check 30-minute expiry.
     */
    @Column(name = "otp_generated_at")
    private LocalDateTime otpGeneratedAt;

    /**
     * When the OTP was verified. Marks the official job start time.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * When the job was completed.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Who cancelled: CUSTOMER | PROVIDER | ADMIN
     */
    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Coin-related fields (Phase 2 wallet system - kept for backward compatibility).
     */
    @Column(name = "coins_used")
    private Integer coinsUsed;

    @Column(name = "hold_id")
    private Long holdId;

    @Column(name = "points_redeemed")
    private Integer pointsRedeemed;

    @Column(name = "points_discount_npr", precision = 12, scale = 2)
    private BigDecimal pointsDiscountNpr;

    /**
     * Comma-separated list of provider IDs that have rejected this booking.
     * Used by dispatch service to skip them during re-assignment.
     * Example: "3,7,12"
     *
     * Better approach in Phase 2: separate booking_rejected_providers table.
     */
    @Column(name = "rejected_provider_ids", length = 500)
    private String rejectedProviderIds;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Helper methods ─────────────────────────────────────────────────────────

    /**
     * Checks if a provider has already rejected this booking.
     */
    public boolean hasProviderRejected(Long providerId) {
        if (rejectedProviderIds == null || rejectedProviderIds.isBlank()) return false;
        String[] ids = rejectedProviderIds.split(",");
        for (String id : ids) {
            if (id.trim().equals(String.valueOf(providerId))) return true;
        }
        return false;
    }

    /**
     * Adds a provider ID to the rejected list.
     */
    public void addRejectedProvider(Long providerId) {
        if (rejectedProviderIds == null || rejectedProviderIds.isBlank()) {
            rejectedProviderIds = String.valueOf(providerId);
        } else {
            rejectedProviderIds = rejectedProviderIds + "," + providerId;
        }
    }
}
