package com.mitra.common;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "broadcast_notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_group", nullable = false)
    private String targetGroup; // ALL_USERS, CITY, CATEGORY

    @Column(name = "filter_value")
    private String filterValue;

    @Column(name = "channel", nullable = false)
    private String channel; // SMS, EMAIL, PUSH

    @Column(name = "title")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "sent_by", nullable = false)
    private String sentBy;
}
