package com.mitra.taskrequests;

import com.mitra.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A TaskRequest is posted by a customer describing a job they need done.
 * Providers browse open tasks and submit Quotes. This replaces the old Booking entity.
 *
 * Status lifecycle: see TaskRequestStatus enum.
 */
@Entity
@Table(
    name = "task_requests",
    indexes = {
        @Index(name = "idx_task_requests_user_id", columnList = "user_id"),
        @Index(name = "idx_task_requests_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    /** Snapshot of service name at task creation time. */
    @Column(name = "service_name")
    private String serviceName;

    /** Snapshot of service category at task creation time (used for provider matching). */
    @Column(name = "category")
    private String category;

    /** Short title, e.g. "Fix leaking kitchen tap" */
    @Column(nullable = false, length = 300)
    private String title;

    /** Detailed description of the job. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Customer's minimum acceptable budget (NPR). */
    @Column(name = "budget_min_npr", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetMinNpr;

    /** Customer's maximum acceptable budget (NPR). */
    @Column(name = "budget_max_npr", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetMaxNpr;

    /** Snapshot of address text. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    /** Latitude of the job location — used for radius-based provider matching. */
    private Double latitude;

    /** Longitude of the job location. */
    private Double longitude;

    /** When the customer wants the job done. */
    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_slots", columnDefinition = "TEXT")
    private String preferredSlots;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskRequestStatus status;

    /** FK to the accepted quote. Set when customer accepts a quote. */
    @Column(name = "accepted_quote_id")
    private Long acceptedQuoteId;

    /** Quotes submitted by providers. Loaded on demand. */
    @OneToMany(mappedBy = "taskRequest", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<Quote> quotes = new ArrayList<>();

    /** Reward points redeemed for discount on this task. */
    @Column(name = "points_redeemed")
    private Integer pointsRedeemed = 0;

    @Column(name = "points_discount_npr", precision = 12, scale = 2)
    private BigDecimal pointsDiscountNpr = BigDecimal.ZERO;

    /** 4-digit OTP generated when quote is accepted. Customer shows to provider to start. */
    @Column(name = "start_otp", length = 6)
    private String startOtp;

    @Column(name = "otp_generated_at")
    private LocalDateTime otpGeneratedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Platform commission (10%). Set on COMPLETED. */
    @Column(name = "platform_fee", precision = 12, scale = 2)
    private BigDecimal platformFee;

    /** Final agreed amount (from accepted quote, after counter-offer if any). */
    @Column(name = "final_amount_npr", precision = 12, scale = 2)
    private BigDecimal finalAmountNpr;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "coupon_discount_npr", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal couponDiscountNpr = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    /** CUSTOMER | PROVIDER | ADMIN */
    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
