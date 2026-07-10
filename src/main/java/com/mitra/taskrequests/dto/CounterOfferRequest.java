package com.mitra.taskrequests.dto;

import lombok.Data;
import java.math.BigDecimal;

/** Request body for customer making a counter-offer on a quote. */
@Data
public class CounterOfferRequest {
    private BigDecimal counterPriceNpr;
}
