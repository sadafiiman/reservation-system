package com.azki.reservation.integration;

import com.azki.reservation.config.ReservationProperties;
import com.azki.reservation.dto.response.ReservationResponse;
import com.azki.reservation.exception.NoAvailableSlotException;
import com.azki.reservation.exception.SlotAlreadyReservedException;
import com.azki.reservation.service.ReservationService;
import com.azki.reservation.service.ReservationTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest extends BaseIntegrationTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ReservationTxService txService;

    @Mock
    private ReservationProperties properties;

    @Mock
    private ReservationResponse response;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                redisTemplate,
                txService,
                properties
        );
    }

    @Test
    void reserveNearestSlot_shouldReserveFromRedis() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(properties.getZsetKey()).thenReturn("available_slots_zset");
        when(properties.getMaxPopRetries()).thenReturn(3);

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);

        when(tuple.getValue()).thenReturn("10");

        when(zSetOperations.popMin("available_slots_zset", 1))
                .thenReturn(Set.of(tuple));

        when(txService.confirm(1L, 10L))
                .thenReturn(response);

        ReservationResponse result =
                reservationService.reserveNearestSlot(1L);

        assertThat(result).isSameAs(response);

        verify(txService).confirm(1L, 10L);
        verify(txService, never()).reserveViaDbFallback(anyLong());
    }

    @Test
    void reserveNearestSlot_shouldFallbackWhenRedisIsEmpty() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(properties.getZsetKey()).thenReturn("available_slots_zset");
        when(properties.getMaxPopRetries()).thenReturn(3);

        when(zSetOperations.popMin("available_slots_zset", 1))
                .thenReturn(Collections.emptySet());

        when(txService.reserveViaDbFallback(1L))
                .thenReturn(response);

        ReservationResponse result =
                reservationService.reserveNearestSlot(1L);

        assertThat(result).isSameAs(response);

        verify(txService).reserveViaDbFallback(1L);
        verify(txService, never()).confirm(anyLong(), anyLong());
    }

    @Test
    void reserveNearestSlot_shouldRetryWhenSlotAlreadyReserved() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(properties.getZsetKey()).thenReturn("available_slots_zset");
        when(properties.getMaxPopRetries()).thenReturn(3);

        ZSetOperations.TypedTuple<String> first = mock(ZSetOperations.TypedTuple.class);
        ZSetOperations.TypedTuple<String> second = mock(ZSetOperations.TypedTuple.class);

        when(first.getValue()).thenReturn("1");
        when(second.getValue()).thenReturn("2");

        when(zSetOperations.popMin("available_slots_zset", 1))
                .thenReturn(Set.of(first))
                .thenReturn(Set.of(second));

        when(txService.confirm(5L, 1L))
                .thenThrow(new SlotAlreadyReservedException(1L));

        when(txService.confirm(5L, 2L))
                .thenReturn(response);

        ReservationResponse result =
                reservationService.reserveNearestSlot(5L);

        assertThat(result).isSameAs(response);

        verify(txService).confirm(5L, 1L);
        verify(txService).confirm(5L, 2L);
    }

    @Test
    void reserveNearestSlot_shouldThrowWhenRetriesExceeded() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(properties.getZsetKey()).thenReturn("available_slots_zset");
        when(properties.getMaxPopRetries()).thenReturn(3);

        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);

        when(tuple.getValue()).thenReturn("99");

        when(zSetOperations.popMin("available_slots_zset", 1))
                .thenReturn(Set.of(tuple))
                .thenReturn(Set.of(tuple))
                .thenReturn(Set.of(tuple));

        when(txService.confirm(7L, 99L))
                .thenThrow(new SlotAlreadyReservedException(99L));

        assertThatThrownBy(() -> reservationService.reserveNearestSlot(7L))
                .isInstanceOf(NoAvailableSlotException.class)
                .hasMessageContaining("Could not reserve a slot");

        verify(txService, times(3))
                .confirm(7L, 99L);
    }

    @Test
    void cancelReservation_shouldDelegateToTxService() {

        reservationService.cancelReservation(100L, 5L);

        verify(txService).cancel(100L, 5L);
    }
}