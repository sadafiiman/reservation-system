package com.azki.reservation.exception;

public class NoAvailableSlotException extends RuntimeException {
    public NoAvailableSlotException(String message) {
        super(message);
    }
}
