package com.mitra.bookings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {
    List<BookingStatusHistory> findByBookingIdOrderByUpdatedAtAsc(Long bookingId);
}
