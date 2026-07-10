package com.mitra.taskrequests.dto;

import lombok.Data;
import java.math.BigDecimal;

/** Request body for provider submitting a quote. */
@Data
public class SubmitQuoteRequest {
    private BigDecimal quotedPriceNpr;
    private String message;
}
