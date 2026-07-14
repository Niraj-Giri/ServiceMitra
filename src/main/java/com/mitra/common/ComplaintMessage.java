package com.mitra.common;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "complaint_messages",
    indexes = {
        @Index(name = "idx_complaint_messages_complaint_id", columnList = "complaint_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "sender_role", nullable = false)
    private String senderRole; // CUSTOMER, PROVIDER, ADMIN

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
