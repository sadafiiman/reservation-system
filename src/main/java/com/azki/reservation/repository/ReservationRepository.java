package com.azki.reservation.repository;

import com.azki.reservation.domain.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Atomic conditional update: only succeeds (returns 1) if the reservation
     * exists, belongs to the caller, and is still CONFIRMED. Avoids a
     * read-then-write race on cancellation.
     */
    @Modifying
    @Query("UPDATE Reservation r SET r.status = 'CANCELLED', r.cancelledAt = :now " +
           "WHERE r.id = :id AND r.userId = :userId AND r.status = 'CONFIRMED'")
    int cancel(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);
}
