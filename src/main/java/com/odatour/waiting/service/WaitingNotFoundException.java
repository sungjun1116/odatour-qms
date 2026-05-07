package com.odatour.waiting.service;

public class WaitingNotFoundException extends RuntimeException {
    public WaitingNotFoundException(Long id) {
        super("Waiting not found: " + id);
    }
}
