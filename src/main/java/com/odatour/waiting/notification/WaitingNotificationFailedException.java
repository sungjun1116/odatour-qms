package com.odatour.waiting.notification;

public class WaitingNotificationFailedException extends RuntimeException {

    public WaitingNotificationFailedException(Long waitingId, Throwable cause) {
        super("Failed to send waiting notification: " + waitingId, cause);
    }
}
