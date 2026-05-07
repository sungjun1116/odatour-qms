package com.odatour.waiting.controller.view;

import java.time.LocalDateTime;

public record WaitingStatusView(
        Long id,
        String maskedPhoneNumber,
        String status,
        String statusLabel,
        Integer remainingCount,
        int estimatedWaitMinutes,
        LocalDateTime createdAt,
        String notice,
        String title,
        boolean cancellable
) {
}
