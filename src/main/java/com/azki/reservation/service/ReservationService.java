package com.azki.reservation.service;

import com.azki.reservation.config.ReservationProperties;
import com.azki.reservation.dto.response.ReservationResponse;
import com.azki.reservation.exception.NoAvailableSlotException;
import com.azki.reservation.exception.SlotAlreadyReservedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Orchestrates a reservation request:
 *   1. Pop the lowest-scored (nearest start_time) slot id from Redis - O(log N), atomic.
 *   2. Confirm it against MySQL inside a real transaction (ReservationTxService).
 *   3. If that specific slot turns out to be already taken (rare race),
 *      discard it and try the next candidate popped from Redis.
 *   4. If Redis has nothing in its window, fall back to a DB-only path
 *      that uses SELECT ... FOR UPDATE SKIP LOCKED.
 *
 * This class intentionally has no @Transactional methods of its own -- all
 * DB transactions live in ReservationTxService, called as an injected bean,
 * so Spring's proxy-based @Transactional always applies correctly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationTxService txService;
    private final ReservationProperties props;

    public ReservationResponse reserveNearestSlot(Long userId) {
        for (int attempt = 0; attempt < props.getMaxPopRetries(); attempt++) {
            Long slotId = popNearestSlotIdFromCache();

            if (slotId == null) {
                log.debug("Redis window empty, falling back to DB SKIP LOCKED path");
                return txService.reserveViaDbFallback(userId);
            }

            try {
                return txService.confirm(userId, slotId);
            } catch (SlotAlreadyReservedException e) {
                log.warn("Candidate slot {} lost a race, retrying (attempt {})", slotId, attempt + 1);
            }
        }
        throw new NoAvailableSlotException("Could not reserve a slot after " + props.getMaxPopRetries() + " attempts");
    }

    public void cancelReservation(Long reservationId, Long userId) {
        txService.cancel(reservationId, userId);
    }

    private Long popNearestSlotIdFromCache() {
        Set<ZSetOperations.TypedTuple<String>> popped =
                redisTemplate.opsForZSet().popMin(props.getZsetKey(), 1);
        if (popped == null || popped.isEmpty()) {
            return null;
        }
        ZSetOperations.TypedTuple<String> tuple = popped.iterator().next();
        return Long.valueOf(tuple.getValue());
    }
}
