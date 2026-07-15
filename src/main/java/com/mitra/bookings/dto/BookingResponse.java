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

    // Finance info
    private String paymentMethod;
    private String paymentStatus;
    private String paymentReceivedBy;
    private String commissionStatus;
    private LocalDateTime commissionDueDate;
    private String settlementStatus;

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

    public static BookingResponse from(com.mitra.taskrequests.TaskRequest taskRequest, boolean showOtp) {
        BookingResponse.CustomerInfo customerInfo = null;
        if (taskRequest.getUser() != null) {
            customerInfo = CustomerInfo.builder()
                    .id(taskRequest.getUser().getId())
                    .name(taskRequest.getUser().getName())
                    .phone(taskRequest.getUser().getPhone())
                    .profilePhoto(taskRequest.getUser().getProfilePhoto())
                    .build();
        }

        BookingResponse.ProviderInfo providerInfo = null;
        if (taskRequest.getAcceptedQuoteId() != null && taskRequest.getQuotes() != null) {
            com.mitra.taskrequests.Quote acceptedQuote = taskRequest.getQuotes().stream()
                    .filter(q -> q.getId().equals(taskRequest.getAcceptedQuoteId()))
                    .findFirst()
                    .orElse(null);
            if (acceptedQuote != null && acceptedQuote.getProvider() != null) {
                providerInfo = ProviderInfo.builder()
                        .id(acceptedQuote.getProvider().getId())
                        .name(acceptedQuote.getProvider().getBusinessName() != null ? acceptedQuote.getProvider().getBusinessName() : acceptedQuote.getProvider().getName())
                        .phone(acceptedQuote.getProvider().getPhone())
                        .profilePhoto(acceptedQuote.getProvider().getProfilePhotoUrl())
                        .rating(acceptedQuote.getProvider().getRatingCache())
                        .totalJobs(acceptedQuote.getProvider().getTotalJobs())
                        .build();
            }
        }

        BigDecimal providerEarnings = null;
        if (taskRequest.getFinalAmountNpr() != null && taskRequest.getPlatformFee() != null) {
            providerEarnings = taskRequest.getFinalAmountNpr().subtract(taskRequest.getPlatformFee());
        }

        BookingStatus status = BookingStatus.PENDING_DISPATCH;
        if (taskRequest.getStatus() != null) {
            switch (taskRequest.getStatus()) {
                case OPEN:
                case QUOTING:
                    status = BookingStatus.PENDING_DISPATCH;
                    break;
                case ACCEPTED:
                    status = BookingStatus.ACCEPTED;
                    break;
                case STARTED:
                    status = BookingStatus.STARTED;
                    break;
                case COMPLETED:
                    status = BookingStatus.COMPLETED;
                    break;
                case CANCELLED:
                    if ("PROVIDER".equalsIgnoreCase(taskRequest.getCancelledBy())) {
                        status = BookingStatus.CANCELLED_BY_PROVIDER;
                    } else if ("ADMIN".equalsIgnoreCase(taskRequest.getCancelledBy())) {
                        status = BookingStatus.CANCELLED_BY_ADMIN;
                    } else {
                        status = BookingStatus.CANCELLED_BY_CUSTOMER;
                    }
                    break;
                case EXPIRED:
                    status = BookingStatus.CANCELLED_BY_ADMIN;
                    break;
            }
        }

        return BookingResponse.builder()
                .id(taskRequest.getId())
                .status(status)
                .serviceId(taskRequest.getServiceId())
                .serviceName(taskRequest.getServiceName() != null ? taskRequest.getServiceName() : taskRequest.getTitle())
                .customer(customerInfo)
                .provider(providerInfo)
                .address(taskRequest.getAddress())
                .notes(taskRequest.getDescription())
                .scheduledAt(taskRequest.getPreferredDate() != null ? taskRequest.getPreferredDate().atStartOfDay() : taskRequest.getCreatedAt())
                .baseAmount(taskRequest.getFinalAmountNpr() != null ? taskRequest.getFinalAmountNpr() : taskRequest.getBudgetMinNpr())
                .platformFee(taskRequest.getPlatformFee())
                .totalBill(taskRequest.getFinalAmountNpr())
                .providerEarnings(providerEarnings)
                .pointsRedeemed(taskRequest.getPointsRedeemed())
                .pointsDiscountNpr(taskRequest.getPointsDiscountNpr())
                .startOtp(showOtp && status == BookingStatus.ACCEPTED ? taskRequest.getStartOtp() : null)
                .paymentMethod(taskRequest.getPaymentMethod())
                .paymentStatus(taskRequest.getPaymentStatus())
                .paymentReceivedBy(taskRequest.getPaymentReceivedBy())
                .startedAt(taskRequest.getStartedAt())
                .completedAt(taskRequest.getCompletedAt())
                .cancelledAt(taskRequest.getCancelledAt())
                .cancelledBy(taskRequest.getCancelledBy())
                .cancellationReason(taskRequest.getCancellationReason())
                .createdAt(taskRequest.getCreatedAt())
                .build();
    }
}
