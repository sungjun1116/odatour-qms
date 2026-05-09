package com.odatour.waiting.controller.view;

public record AdminSummary(
        int total,
        long waiting,
        long called,
        long arrived,
        int totalPages
) {
}
