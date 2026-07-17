package com.azki.reservation.repository;

import com.azki.reservation.domain.slot.AvailableSlot;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AvailableSlotRepository extends JpaRepository<AvailableSlot, Long> {

    /**
     * Row-level pessimistic lock. Used right before mutating a slot that we
     * believe (via Redis or the fallback query) is free — protects against
     * the rare case where Redis and the DB briefly disagree.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT s FROM AvailableSlot s WHERE s.id = :id")
    Optional<AvailableSlot> lockById(@Param("id") Long id);

    /**
     * Used by the scheduled cache-sync job to (re)populate the Redis ZSET
     * window. Relies on idx_reserved_start(is_reserved, start_time).
     */
    @Query("SELECT s FROM AvailableSlot s WHERE s.reserved = false " +
           "AND s.startTime BETWEEN :from AND :to ORDER BY s.startTime ASC")
    List<AvailableSlot> findAvailableInWindow(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /**
     * DB-only fallback path for when Redis is empty/unavailable. SKIP LOCKED
     * means concurrent callers never queue behind each other's row locks —
     * each one grabs the next unlocked candidate instead.
     */
    @Query(value = "SELECT * FROM available_slots " +
           "WHERE is_reserved = FALSE AND start_time >= :now " +
           "ORDER BY start_time ASC LIMIT 1 FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    Optional<AvailableSlot> findNearestAvailableSkipLocked(@Param("now") LocalDateTime now);
}
