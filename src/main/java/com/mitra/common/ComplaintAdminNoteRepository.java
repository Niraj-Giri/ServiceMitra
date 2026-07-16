package com.mitra.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplaintAdminNoteRepository extends JpaRepository<ComplaintAdminNote, Long> {
    List<ComplaintAdminNote> findByComplaintIdOrderByCreatedAtDesc(Long complaintId);
}
