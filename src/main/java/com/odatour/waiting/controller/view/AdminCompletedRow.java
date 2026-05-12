package com.odatour.waiting.controller.view;

import java.time.LocalDateTime;

public record AdminCompletedRow(
        Long id,
        String phoneNumber,
        String status,
        String statusLabel,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        boolean revertable
) {
}
