package com.mitra.common;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaint_admin_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintAdminNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complaint_id", nullable = false)
    private Long complaintId;

    @Column(name = "admin_name", nullable = false, length = 100)
    private String adminName;

    @Column(name = "note_text", nullable = false, columnDefinition = "TEXT")
    private String noteText;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
