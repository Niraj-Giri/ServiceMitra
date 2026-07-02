package com.mitra.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import com.mitra.entity.ChatMessage;
import com.mitra.entity.ChatMessageRepository;
import java.time.LocalDateTime;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // WebSocket endpoint to receive a message and broadcast it
    @MessageMapping("/chat/{bookingId}")
    public void processMessage(@DestinationVariable Long bookingId, @Payload ChatMessage message) {
        message.setBookingId(bookingId);
        message.setTimestamp(LocalDateTime.now());
        
        // Save to DB
        ChatMessage savedMsg = chatMessageRepository.save(message);
        
        // Broadcast to specific booking chat room
        messagingTemplate.convertAndSend("/topic/chat/" + bookingId, savedMsg);
    }

    // REST endpoint to get chat history
    @GetMapping("/api/chat/{bookingId}")
    public ResponseEntity<?> getChatHistory(@PathVariable Long bookingId) {
        return ResponseEntity.ok(chatMessageRepository.findByBookingIdOrderByTimestampAsc(bookingId));
    }
}
