package com.mitra.taskrequests.dto;

import lombok.Data;

@Data
public class TaskCheckoutRequest {
    private String couponCode;
    private Boolean redeemPoints;
    private String paymentMethod; // COD, ESEWA, KHALTI
}
