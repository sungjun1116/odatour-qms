package com.odatour.waiting.domain;

import java.time.LocalDateTime;

public record WaitingEntry(
        Long id,
        String phoneNumber,
        boolean consentAgreed,
        WaitingStatus status,
        LocalDateTime notifiedAt,
        LocalDateTime enteredAt,
        LocalDateTime noShowAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
