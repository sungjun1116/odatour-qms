package com.odatour.waiting.waiting;

public class WaitingNotFoundException extends RuntimeException {
    public WaitingNotFoundException(Long id) {
        super("Waiting not found: " + id);
    }
}
