package com.odatour.waiting.service;

import com.odatour.waiting.domain.WaitingEntry;

public class DuplicateActiveWaitingException extends RuntimeException {
    private final WaitingEntry activeWaiting;

    public DuplicateActiveWaitingException(WaitingEntry activeWaiting) {
        super("Already has active waiting: " + activeWaiting.id());
        this.activeWaiting = activeWaiting;
    }

    public WaitingEntry activeWaiting() {
        return activeWaiting;
    }
}
