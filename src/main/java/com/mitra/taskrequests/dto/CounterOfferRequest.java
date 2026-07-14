package com.mitra.taskrequests.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Request body for customer making a counter-offer on a quote.
 *
 * VAL-01: Added validation annotations to ensure a price is submitted
 * and avoids null comparison crashes in the service layer.
 */
@Data
public class CounterOfferRequest {
    @NotNull(message = "Counter-offer price is required")
    @Positive(message = "Counter-offer price must be greater than zero")
    private BigDecimal counterPriceNpr;
}
