package com.azki.reservation.service;

import com.azki.reservation.config.ReservationProperties;
import com.azki.reservation.domain.slot.AvailableSlot;
import com.azki.reservation.repository.AvailableSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps the Redis ZSET in sync with MySQL.
 * <p>
 * Why this exists: the reservation hot path (ReservationService) treats
 * Redis as a fast index and MySQL as the source of truth. If a request dies
 * between ZPOPMIN and the DB commit (e.g. pod crash), that slot id is lost
 * from the cache even though it's still genuinely available in the DB. This
 * job re-adds it within its next run (default: every 30s), so the system
 * self-heals without any manual intervention. ZADD is idempotent, so
 * resyncing already-cached slots is a no-op.
 * <p>
 * Only a rolling window (not all 1M+ rows) is mirrored, keeping Redis memory
 * bounded and this query index-only (idx_reserved_start).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlotCacheSyncJob {

    private final RedisTemplate<String, String> redisTemplate;
    private final AvailableSlotRepository slotRepository;
    private final ReservationProperties props;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        syncWindow();
    }

    @Scheduled(fixedDelayString = "PT30S") // I'd move it to configuration later.
    public void syncWindow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusDays(props.getSlotWindowDays());

        List<AvailableSlot> available = slotRepository.findAvailableInWindow(now, windowEnd);

        Set<ZSetOperations.TypedTuple<String>> tuples = available.stream()
                .map(s -> ZSetOperations.TypedTuple.of(
                        String.valueOf(s.getId()),
                        (double) s.getStartTime().toEpochSecond(ZoneOffset.UTC)))
                .collect(Collectors.toSet());

        if (!tuples.isEmpty()) {
            redisTemplate.opsForZSet().add(props.getZsetKey(), tuples);
        }

        // Drop anything whose start_time has already passed out of the window.
        redisTemplate.opsForZSet().removeRangeByScore(
                props.getZsetKey(), 0, now.toEpochSecond(ZoneOffset.UTC));

        log.debug("Slot cache synced: {} slots re-affirmed in window [{}, {}]",
                tuples.size(), now, windowEnd);
    }
}
