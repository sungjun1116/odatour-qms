package com.odatour.waiting.controller.view;

public record AdminSummary(
        int total,
        long waiting,
        long called,
        int totalPages
) {
}
