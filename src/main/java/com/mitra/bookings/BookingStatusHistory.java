package com.mitra.bookings;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy; // CUSTOMER, PROVIDER, ADMIN, SYSTEM

    @Column(name = "notes")
    private String notes;
}
