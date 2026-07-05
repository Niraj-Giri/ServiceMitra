package com.mitra.bookings;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BookingRequestDto {
    private Long userId;
    private Long providerId;
    private Long serviceId;
    private LocalDateTime scheduledAt;
    private BigDecimal amountNpr;
    private Integer coinsUsed;
    private Long coinAccountId; // Needed for createHold
    private Long addressId;
    private String address;
    private String notes;
}
