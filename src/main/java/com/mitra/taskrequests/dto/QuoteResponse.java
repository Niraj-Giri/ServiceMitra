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

    // Provider info
    private Long providerId;
    private String providerName;
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
        return QuoteResponse.builder()
                .id(q.getId())
                .taskRequestId(q.getTaskRequest().getId())
                .status(q.getStatus())
                .providerId(q.getProvider().getId())
                .providerName(q.getProvider().getName())
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
