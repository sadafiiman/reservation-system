package com.azki.reservation.integration;

import com.azki.reservation.domain.slot.AvailableSlot;
import com.azki.reservation.domain.user.User;
import com.azki.reservation.repository.AvailableSlotRepository;
import com.azki.reservation.repository.ReservationRepository;
import com.azki.reservation.repository.UserRepository;
import com.azki.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentReservation extends BaseIntegrationTest {

    private static final int SLOT_COUNT = 20;
    private static final int USER_COUNT = 100;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private AvailableSlotRepository slotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    private List<Long> userIds;

    @BeforeEach
    void setUp() {

        reservationRepository.deleteAll();
        slotRepository.deleteAll();
        userRepository.deleteAll();

        userIds = new ArrayList<>();

        for (int i = 0; i < USER_COUNT; i++) {
            User user = new User();
            user.setUsername("user-" + i);
            user.setEmail("user-" + i + "@test.com");
            user.setPassword("password");

            userRepository.save(user);
            userIds.add(user.getId());
        }

        LocalDateTime start = LocalDateTime.now().plusHours(1);

        for (int i = 0; i < SLOT_COUNT; i++) {
            AvailableSlot slot = new AvailableSlot();
            slot.setStartTime(start.plusHours(i));
            slot.setEndTime(start.plusHours(i + 1));
            slot.setReserved(false);

            slotRepository.save(slot);
        }
    }

    @Test
    void exactlySlotCountReservationsSucceedUnderConcurrency() {

        ExecutorService executor = Executors.newFixedThreadPool(32);

        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        List<CompletableFuture<Void>> futures = userIds.stream()
                .map(userId -> CompletableFuture.runAsync(() -> {

                    try {
                        startLatch.await();

                        reservationService.reserveNearestSlot(userId);

                        success.incrementAndGet();

                    } catch (Exception ignored) {
                        failure.incrementAndGet();
                    }

                }, executor))
                .toList();

        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();

        assertThat(success.get()).isEqualTo(SLOT_COUNT);
        assertThat(failure.get()).isEqualTo(USER_COUNT - SLOT_COUNT);

        assertThat(
                slotRepository.findAll()
                        .stream()
                        .filter(AvailableSlot::isReserved)
                        .count()
        ).isEqualTo(SLOT_COUNT);

        assertThat(
                reservationRepository.findAll()
                        .stream()
                        .map(r -> r.getSlotId())
                        .distinct()
                        .count()
        ).isEqualTo(SLOT_COUNT);
    }
}