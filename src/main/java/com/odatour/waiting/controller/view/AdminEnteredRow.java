package com.odatour.waiting.controller.view;

import java.time.LocalDateTime;

public record AdminEnteredRow(
        Long id,
        String maskedPhoneNumber,
        String status,
        String statusLabel,
        LocalDateTime createdAt,
        LocalDateTime enteredAt
) {
}
