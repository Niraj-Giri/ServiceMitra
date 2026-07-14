package com.mitra.users;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a customer (end user who books services).
 *
 * NOTE: Kept as 'users' table name for backward DB compatibility.
 * The 'password' column is kept in DB but never populated (OTP-only auth).
 * 'role' column defaults to 'CUSTOMER' — only used for legacy data.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_phone", columnList = "phone"),
        @Index(name = "idx_users_email", columnList = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    /**
     * Kept for DB backward compatibility. Never populated in new code.
     * OTP-based auth does not use passwords.
     */
    @Column(nullable = false)
    private String password;

    @Column(name = "phone", unique = true)
    private String phone;

    @Column(name = "profile_photo")
    private String profilePhoto;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    public Boolean getIsActive() {
        return isActive == null || isActive;
    }

    /**
     * Always "CUSTOMER" for users in this table.
     * Providers are in a separate 'providers' table.
     */
    @Column(nullable = false)
    private String role;

    @Column(name = "reward_points", nullable = false)
    @Builder.Default
    private Integer rewardPoints = 0;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
