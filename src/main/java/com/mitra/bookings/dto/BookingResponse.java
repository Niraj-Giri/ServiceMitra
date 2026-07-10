package com.mitra.bookings.dto;

import com.mitra.bookings.Booking;
import com.mitra.bookings.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full booking detail response. Used for single booking fetch and after status transitions.
 * Never exposes raw JPA entities to prevent lazy load issues and accidental field exposure.
 */
@Data
@Builder
public class BookingResponse {

    private Long id;
    private BookingStatus status;

    // Service snapshot
    private Long serviceId;
    private String serviceName;

    // Customer info (always included)
    private CustomerInfo customer;

    // Provider info (included only after ACCEPTED — controlled at service layer)
    private ProviderInfo provider;

    // Booking details
    private String address;
    private String notes;
    private LocalDateTime scheduledAt;

    // Financial (populated on COMPLETED)
    private BigDecimal baseAmount;
    private BigDecimal platformFee;
    private BigDecimal totalBill;
    private BigDecimal providerEarnings; // baseAmount - platformFee
    
    // Reward points
    private Integer pointsRedeemed;
    private BigDecimal pointsDiscountNpr;

    // OTP (only returned for customer's own booking when status = ACCEPTED)
    private String startOtp;

    // Timestamps
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String cancellationReason;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class CustomerInfo {
        private Long id;
        private String name;
        private String phone;      // Only visible to provider after ACCEPTED
        private String profilePhoto;
    }

    @Data
    @Builder
    public static class ProviderInfo {
        private Long id;
        private String name;
        private String phone;      // Only visible to customer after ACCEPTED
        private String profilePhoto;
        private BigDecimal rating;
        private Integer totalJobs;
    }

    /**
     * Factory method to map a Booking entity to a BookingResponse.
     *
     * @param booking    The booking entity (must be fetched with user/provider in same transaction)
     * @param showOtp    Whether to include the OTP (true only for customer fetching their own booking)
     * @param showPhone  Whether to show contact phone numbers (true only when status >= ACCEPTED)
     */
    public static BookingResponse from(Booking booking, boolean showOtp, boolean showPhone) {
        BookingResponse.CustomerInfo customerInfo = null;
        if (booking.getUser() != null) {
            customerInfo = CustomerInfo.builder()
                    .id(booking.getUser().getId())
                    .name(booking.getUser().getName())
                    .phone(showPhone ? booking.getUser().getPhone() : null)
                    .profilePhoto(booking.getUser().getProfilePhoto())
                    .build();
        }

        BookingResponse.ProviderInfo providerInfo = null;
        if (booking.getProvider() != null) {
            providerInfo = ProviderInfo.builder()
                    .id(booking.getProvider().getId())
                    .name(booking.getProvider().getBusinessName())
                    .phone(showPhone ? booking.getProvider().getPhone() : null)
                    .profilePhoto(booking.getProvider().getProfilePhotoUrl())
                    .rating(booking.getProvider().getRatingCache())
                    .totalJobs(booking.getProvider().getTotalJobs())
                    .build();
        }

        BigDecimal providerEarnings = null;
        if (booking.getTotalBill() != null && booking.getPlatformFee() != null) {
            providerEarnings = booking.getTotalBill().subtract(booking.getPlatformFee());
        }

        BookingStatus status = booking.getStatus();
        boolean contactVisible = status == BookingStatus.ACCEPTED
                || status == BookingStatus.STARTED
                || status == BookingStatus.COMPLETED;

        return BookingResponse.builder()
                .id(booking.getId())
                .status(booking.getStatus())
                .serviceId(booking.getServiceId())
                .serviceName(booking.getServiceName())
                .customer(customerInfo)
                .provider(providerInfo)
                .address(booking.getAddress())
                .notes(booking.getNotes())
                .scheduledAt(booking.getScheduledAt())
                .baseAmount(booking.getAmountNpr())
                .platformFee(booking.getPlatformFee())
                .totalBill(booking.getTotalBill())
                .providerEarnings(providerEarnings)
                .pointsRedeemed(booking.getPointsRedeemed())
                .pointsDiscountNpr(booking.getPointsDiscountNpr())
                .startOtp(showOtp && status == BookingStatus.ACCEPTED ? booking.getStartOtp() : null)
                .startedAt(booking.getStartedAt())
                .completedAt(booking.getCompletedAt())
                .cancelledAt(booking.getCancelledAt())
                .cancelledBy(booking.getCancelledBy())
                .cancellationReason(booking.getCancellationReason())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
