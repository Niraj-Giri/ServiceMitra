package com.mitra.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private Long bookingId;
    private Long senderId;
    private String senderRole; // "CUSTOMER" or "PROVIDER"
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    private LocalDateTime timestamp = LocalDateTime.now();
}
