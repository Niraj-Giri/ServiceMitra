package com.mitra.chat;

import com.mitra.bookings.Booking;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_request_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.mitra.taskrequests.TaskRequest taskRequest;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "sender_role", nullable = false)
    private String senderRole;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
