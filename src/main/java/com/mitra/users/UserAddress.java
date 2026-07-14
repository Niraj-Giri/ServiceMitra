package com.mitra.users;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "user_addresses",
    indexes = {
        @Index(name = "idx_user_addresses_user_id", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String label;

    private String line1;

    private Double latitude;

    private Double longitude;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
