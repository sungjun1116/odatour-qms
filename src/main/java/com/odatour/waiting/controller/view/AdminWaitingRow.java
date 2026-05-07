package com.odatour.waiting.controller.view;

import java.time.LocalDateTime;

public record AdminWaitingRow(
        Long id,
        String maskedPhoneNumber,
        String status,
        String statusLabel,
        Integer remainingCount,
        LocalDateTime createdAt,
        boolean actionable
) {
}
