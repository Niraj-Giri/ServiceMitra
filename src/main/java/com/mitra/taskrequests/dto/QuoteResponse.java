package com.mitra.taskrequests.dto;

import com.mitra.taskrequests.Quote;
import com.mitra.taskrequests.QuoteStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Response DTO for a single quote, enriched with provider info. */
@Data
@Builder
public class QuoteResponse {

    private Long id;
    private Long taskRequestId;
    private QuoteStatus status;

    // Task details context
    private String taskTitle;
    private String taskDescription;
    private String taskStatus;
    private BigDecimal taskBudgetMinNpr;
    private BigDecimal taskBudgetMaxNpr;
    private String customerName;

    // Provider info
    private Long providerId;
    private String providerName;
    private String providerPhone;
    private String providerBusinessName;
    private String providerProfilePhoto;
    private BigDecimal providerRating;
    private Integer providerTotalJobs;
    private Integer providerExperienceYears;

    // Pricing
    private BigDecimal quotedPriceNpr;
    private BigDecimal counterPriceNpr;
    private BigDecimal finalPriceNpr;

    private String message;
    private LocalDateTime createdAt;

    public static QuoteResponse from(Quote q) {
        String title = null;
        String desc = null;
        String tStatus = null;
        BigDecimal budgetMin = null;
        BigDecimal budgetMax = null;
        String custName = null;

        if (q.getTaskRequest() != null) {
            title = q.getTaskRequest().getTitle();
            desc = q.getTaskRequest().getDescription();
            if (q.getTaskRequest().getStatus() != null) {
                tStatus = q.getTaskRequest().getStatus().name();
            }
            budgetMin = q.getTaskRequest().getBudgetMinNpr();
            budgetMax = q.getTaskRequest().getBudgetMaxNpr();
            if (q.getTaskRequest().getUser() != null) {
                custName = q.getTaskRequest().getUser().getName();
            }
        }

        return QuoteResponse.builder()
                .id(q.getId())
                .taskRequestId(q.getTaskRequest() != null ? q.getTaskRequest().getId() : null)
                .status(q.getStatus())
                .taskTitle(title)
                .taskDescription(desc)
                .taskStatus(tStatus)
                .taskBudgetMinNpr(budgetMin)
                .taskBudgetMaxNpr(budgetMax)
                .customerName(custName)
                .providerId(q.getProvider().getId())
                .providerName(q.getProvider().getName())
                .providerPhone(q.getProvider().getPhone())
                .providerBusinessName(q.getProvider().getBusinessName())
                .providerProfilePhoto(q.getProvider().getProfilePhotoUrl())
                .providerRating(q.getProvider().getRatingCache())
                .providerTotalJobs(q.getProvider().getTotalJobs())
                .providerExperienceYears(q.getProvider().getExperienceYears())
                .quotedPriceNpr(q.getQuotedPriceNpr())
                .counterPriceNpr(q.getCounterPriceNpr())
                .finalPriceNpr(q.getFinalPriceNpr())
                .message(q.getMessage())
                .createdAt(q.getCreatedAt())
                .build();
    }
}
