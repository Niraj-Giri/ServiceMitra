package com.mitra.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
    List<ChatMessage> findByTaskRequestIdOrderByCreatedAtAsc(Long taskRequestId);
}
