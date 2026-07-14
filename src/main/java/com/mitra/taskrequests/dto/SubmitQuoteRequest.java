package com.mitra.taskrequests.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Request body for provider submitting a quote.
 *
 * VAL-01: Added validation annotations to ensure a price is submitted
 * and avoids null comparison crashes in the service layer.
 */
@Data
public class SubmitQuoteRequest {
    @NotNull(message = "Quote price is required")
    @Positive(message = "Quote price must be greater than zero")
    private BigDecimal quotedPriceNpr;

    private String message;
}
