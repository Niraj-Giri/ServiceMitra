-- V17: Simplified Complaint System schema alterations

-- 1. Add resolution_remarks to complaints
ALTER TABLE complaints ADD COLUMN resolution_remarks TEXT NULL;

-- 2. Create complaint_admin_notes table
CREATE TABLE complaint_admin_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id BIGINT NOT NULL,
    admin_name VARCHAR(100) NOT NULL,
    note_text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaint_notes_complaint FOREIGN KEY (complaint_id) REFERENCES complaints(id) ON DELETE CASCADE
);
