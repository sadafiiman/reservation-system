package com.azki.reservation.exception;

public class SlotAlreadyReservedException extends RuntimeException {
    public SlotAlreadyReservedException(Long slotId) {
        super("Slot already reserved: " + slotId);
    }
}
