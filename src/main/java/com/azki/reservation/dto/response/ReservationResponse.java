package com.azki.reservation.dto.response;

import com.azki.reservation.domain.reservation.Reservation;
import com.azki.reservation.domain.reservation.ReservationStatus;
import com.azki.reservation.domain.slot.AvailableSlot;

import java.time.LocalDateTime;

public record ReservationResponse(
        Long reservationId,
        Long slotId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ReservationStatus status
) {
    public static ReservationResponse from(Reservation reservation, AvailableSlot slot) {
        return new ReservationResponse(
                reservation.getId(),
                slot.getId(),
                slot.getStartTime(),
                slot.getEndTime(),
                reservation.getStatus()
        );
    }
}
