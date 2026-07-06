package com.mitra.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComplaintMessageRepository extends JpaRepository<ComplaintMessage, Long> {
    List<ComplaintMessage> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);
}
