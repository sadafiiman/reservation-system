package com.azki.reservation.service;

import com.azki.reservation.config.ReservationProperties;
import com.azki.reservation.domain.reservation.Reservation;
import com.azki.reservation.domain.reservation.ReservationStatus;
import com.azki.reservation.domain.slot.AvailableSlot;
import com.azki.reservation.dto.response.ReservationResponse;
import com.azki.reservation.exception.NoAvailableSlotException;
import com.azki.reservation.exception.ReservationNotFoundException;
import com.azki.reservation.exception.SlotAlreadyReservedException;
import com.azki.reservation.exception.SlotNotFoundException;
import com.azki.reservation.repository.AvailableSlotRepository;
import com.azki.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Holds every method that needs a real DB transaction. Kept as a separate
 * bean from ReservationService (which does the Redis pop/retry loop) so that
 * @Transactional is never skipped by Spring's self-invocation limitation --
 * a call from ReservationService into this bean always goes through the
 * transactional proxy.
 */
@Service
@RequiredArgsConstructor
public class ReservationTxService {

    private final AvailableSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationProperties props;

    @Transactional
    public ReservationResponse confirm(Long userId, Long slotId) {
        // Defensive row lock: in the overwhelming majority of calls the slot
        // is already "ours" (Redis just handed us an exclusive id via
        // ZPOPMIN), so this lock is uncontended and cheap. It only matters
        // in the rare case of a Redis/DB drift or the DB fallback path.
        AvailableSlot slot = slotRepository.lockById(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (slot.isReserved()) {
            throw new SlotAlreadyReservedException(slotId);
        }

        slot.setReserved(true);
        slotRepository.save(slot); // actually no need to slotRepository.save(slot) Hibernate dirty checking will update automatically.

        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSlotId(slotId);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(LocalDateTime.now());

        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // Last line of defense: the partial unique index on
            // active_slot_id caught a race that somehow slipped past the
            // row lock (e.g. a non-locking write path). Roll back and
            // signal the caller to retry with the next candidate slot.
            throw new SlotAlreadyReservedException(slotId);
        }

        return ReservationResponse.from(reservation, slot);
    }

    @Transactional
    public ReservationResponse reserveViaDbFallback(Long userId) {
        AvailableSlot slot = slotRepository.findNearestAvailableSkipLocked(LocalDateTime.now())
                .orElseThrow(() -> new NoAvailableSlotException("No available slots at this time"));
        return confirm(userId, slot.getId());
    }

    @Transactional
    public void cancel(Long reservationId, Long userId) {
        int updated = reservationRepository.cancel(reservationId, userId, LocalDateTime.now());
        if (updated == 0) {
            throw new ReservationNotFoundException(reservationId);
        }

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        AvailableSlot slot = slotRepository.lockById(reservation.getSlotId())
                .orElseThrow(() -> new SlotNotFoundException(reservation.getSlotId()));
        slot.setReserved(false);
        slotRepository.save(slot); // actually no need to slotRepository.save(slot) Hibernate dirty checking will flush automatically.

        // Only return the slot to the cache if it's still within the active
        // window and in the future -- keeps Redis from re-accumulating
        // stale/past entries.
        if (slot.getStartTime().isAfter(LocalDateTime.now())) {
            double score = slot.getStartTime().toEpochSecond(ZoneOffset.UTC);
            redisTemplate.opsForZSet().add(props.getZsetKey(), String.valueOf(slot.getId()), score);
        }
    }
}
