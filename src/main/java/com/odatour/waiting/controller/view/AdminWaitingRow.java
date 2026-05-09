package com.odatour.waiting.controller.view;

import java.time.LocalDateTime;

public record AdminWaitingRow(
        Long id,
        String maskedPhoneNumber,
        String status,
        String statusLabel,
        Integer remainingCount,
        LocalDateTime createdAt,
        LocalDateTime notifiedAt,
        String callElapsedLabel,
        boolean callOverdue,
        boolean notifiable,
        boolean arrivable,
        boolean enterable,
        boolean noShowable,
        boolean cancelable,
        boolean actionable
) {
}
