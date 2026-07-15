package com.mitra.users;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", unique = true, nullable = false)
    private Long providerId;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "online_earnings", precision = 12, scale = 2)
    private BigDecimal onlineEarnings;

    @Column(name = "cod_earnings", precision = 12, scale = 2)
    private BigDecimal codEarnings;

    @Column(name = "outstanding_commission", precision = 12, scale = 2)
    private BigDecimal outstandingCommission;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
