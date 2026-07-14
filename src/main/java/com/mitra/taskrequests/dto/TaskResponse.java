package com.mitra.taskrequests.dto;

import com.mitra.taskrequests.TaskRequest;
import com.mitra.taskrequests.TaskRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Full task request response, including all quotes.
 * Sensitive fields (OTP, contact info) are controlled by showOtp / showPhone flags.
 */
@Data
@Builder
public class TaskResponse {

    private Long id;
    private TaskRequestStatus status;

    // Service snapshot
    private Long serviceId;
    private String serviceName;
    private String category;

    // Job details
    private String title;
    private String description;
    private BigDecimal budgetMinNpr;
    private BigDecimal budgetMaxNpr;
    private String address;
    private Double latitude;
    private Double longitude;
    private LocalDate preferredDate;
    private List<String> preferredSlots;

    // Customer info
    private CustomerInfo customer;

    // Quotes
    private List<QuoteResponse> quotes;
    private Long acceptedQuoteId;

    // Financial (after completion)
    private BigDecimal finalAmountNpr;
    private BigDecimal platformFee;
    private Integer pointsRedeemed;
    private BigDecimal pointsDiscountNpr;
    private String couponCode;
    private BigDecimal couponDiscountNpr;
    private String paymentMethod;

    // OTP (only for customer when status = ACCEPTED)
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
        private String phone;
        private String profilePhoto;
    }

    public static TaskResponse from(TaskRequest tr, boolean showOtp) {
        CustomerInfo customerInfo = null;
        if (tr.getUser() != null) {
            customerInfo = CustomerInfo.builder()
                    .id(tr.getUser().getId())
                    .name(tr.getUser().getName())
                    .phone(tr.getUser().getPhone())
                    .profilePhoto(tr.getUser().getProfilePhoto())
                    .build();
        }

        List<QuoteResponse> quoteResponses = tr.getQuotes() == null ? List.of() :
                tr.getQuotes().stream().map(QuoteResponse::from).collect(Collectors.toList());

        TaskRequestStatus status = tr.getStatus();
        boolean otpVisible = showOtp && status == TaskRequestStatus.ACCEPTED && tr.getStartOtp() != null;

        return TaskResponse.builder()
                .id(tr.getId())
                .status(status)
                .serviceId(tr.getServiceId())
                .serviceName(tr.getServiceName())
                .category(tr.getCategory())
                .title(tr.getTitle())
                .description(tr.getDescription())
                .budgetMinNpr(tr.getBudgetMinNpr())
                .budgetMaxNpr(tr.getBudgetMaxNpr())
                .address(tr.getAddress())
                .latitude(tr.getLatitude())
                .longitude(tr.getLongitude())
                .preferredDate(tr.getPreferredDate())
                .preferredSlots(tr.getPreferredSlots() == null || tr.getPreferredSlots().isEmpty()
                        ? List.of()
                        : java.util.Arrays.asList(tr.getPreferredSlots().split(",")))
                .customer(customerInfo)
                .quotes(quoteResponses)
                .acceptedQuoteId(tr.getAcceptedQuoteId())
                .finalAmountNpr(tr.getFinalAmountNpr())
                .platformFee(tr.getPlatformFee())
                .pointsRedeemed(tr.getPointsRedeemed())
                .pointsDiscountNpr(tr.getPointsDiscountNpr())
                .couponCode(tr.getCouponCode())
                .couponDiscountNpr(tr.getCouponDiscountNpr())
                .paymentMethod(tr.getPaymentMethod())
                .startOtp(otpVisible ? tr.getStartOtp() : null)
                .startedAt(tr.getStartedAt())
                .completedAt(tr.getCompletedAt())
                .cancelledAt(tr.getCancelledAt())
                .cancelledBy(tr.getCancelledBy())
                .cancellationReason(tr.getCancellationReason())
                .createdAt(tr.getCreatedAt())
                .build();
    }
}
