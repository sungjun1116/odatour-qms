package com.odatour.waiting.controller.view;

import java.util.List;

public record PageView<T>(
        List<T> items,
        int currentPage,
        int totalPages,
        int totalItems,
        int pageSize,
        boolean hasPrevious,
        boolean hasNext,
        int previousPage,
        int nextPage,
        List<Integer> pageNumbers
) {
}
