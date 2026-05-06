package com.odatour.waiting.web;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WaitingPageController {

    private static final int PAGE_SIZE = 10;

    @GetMapping("/")
    public String newWaiting(Model model) {
        model.addAttribute("estimatedWaitMinutes", 15);
        return "waitings/new";
    }

    @PostMapping("/waitings")
    public String createWaiting() {
        return "redirect:/waitings/13?status=WAITING";
    }

    @GetMapping("/waitings/{id}")
    public String waitingStatus(
            @PathVariable Long id,
            @RequestParam(defaultValue = "CALLED") String status,
            Model model
    ) {
        String normalizedStatus = normalizeStatus(status);
        model.addAttribute("waiting", waitingStatusView(id, normalizedStatus));
        return "waitings/status";
    }

    @PostMapping("/waitings/{id}/cancel")
    public String cancelWaiting(@PathVariable Long id) {
        return "redirect:/waitings/" + id + "?status=CANCELED";
    }

    @GetMapping("/admin/waitings")
    public String adminWaitings(@RequestParam(defaultValue = "1") int page, Model model) {
        List<AdminWaitingRow> activeWaitings = activeWaitingRows();
        PageView<AdminWaitingRow> pageView = paginate(activeWaitings, page);

        model.addAttribute("summary", new AdminSummary(
                activeWaitings.size(),
                countByStatus(activeWaitings, "WAITING"),
                countByStatus(activeWaitings, "CALLED"),
                pageView.totalPages()
        ));
        model.addAttribute("waitings", pageView.items());
        model.addAttribute("page", pageView);
        model.addAttribute("enteredCount", enteredWaitingRows().size());
        return "admin/waitings";
    }

    @GetMapping("/admin/waitings/entered")
    public String enteredWaitings(@RequestParam(defaultValue = "1") int page, Model model) {
        PageView<AdminEnteredRow> pageView = paginate(enteredWaitingRows(), page);

        model.addAttribute("enteredWaitings", pageView.items());
        model.addAttribute("page", pageView);
        model.addAttribute("activeCount", activeWaitingRows().size());
        return "admin/entered-waitings";
    }

    @PostMapping("/admin/waitings/{id}/enter")
    public String enter(@PathVariable Long id) {
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/no-show")
    public String noShow(@PathVariable Long id) {
        return "redirect:/admin/waitings";
    }

    private WaitingStatusView waitingStatusView(Long id, String status) {
        return switch (status) {
            case "WAITING" -> new WaitingStatusView(
                    id,
                    "010-****-1234",
                    "WAITING",
                    "대기 중",
                    6,
                    LocalDateTime.now().minusMinutes(3),
                    "앞 순서가 가까워지면 SMS로 알려드립니다.",
                    "6번째로 대기 중입니다",
                    true
            );
            case "ENTERED" -> new WaitingStatusView(
                    id,
                    "010-****-1234",
                    "ENTERED",
                    "입장 완료",
                    null,
                    LocalDateTime.now().minusMinutes(25),
                    "입장 처리가 완료되었습니다.",
                    "입장 완료되었습니다",
                    false
            );
            case "NO_SHOW" -> new WaitingStatusView(
                    id,
                    "010-****-1234",
                    "NO_SHOW",
                    "노쇼 처리",
                    null,
                    LocalDateTime.now().minusMinutes(25),
                    "현장 관리자에 의해 노쇼 처리되었습니다.",
                    "노쇼 처리되었습니다",
                    false
            );
            case "CANCELED" -> new WaitingStatusView(
                    id,
                    "010-****-1234",
                    "CANCELED",
                    "취소 완료",
                    null,
                    LocalDateTime.now().minusMinutes(12),
                    "웨이팅 취소가 완료되었습니다.",
                    "웨이팅이 취소되었습니다",
                    false
            );
            default -> new WaitingStatusView(
                    id,
                    "010-****-1234",
                    "CALLED",
                    "입장 준비",
                    3,
                    LocalDateTime.now().minusMinutes(8),
                    "호출 SMS를 발송했습니다. 현장 직원 안내에 따라 입장해 주세요.",
                    "3번째로 대기 중입니다",
                    true
            );
        };
    }

    private String normalizeStatus(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "WAITING", "CALLED", "ENTERED", "NO_SHOW", "CANCELED" -> status.toUpperCase(Locale.ROOT);
            default -> "CALLED";
        };
    }

    private List<AdminWaitingRow> activeWaitingRows() {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                new AdminWaitingRow(1L, "010-****-4412", "CALLED", "호출 완료", 0, now.minusMinutes(44), true),
                new AdminWaitingRow(2L, "010-****-9021", "CALLED", "호출 완료", 1, now.minusMinutes(41), true),
                new AdminWaitingRow(3L, "010-****-1188", "CALLED", "호출 완료", 2, now.minusMinutes(37), true),
                new AdminWaitingRow(4L, "010-****-7730", "CALLED", "호출 완료", 3, now.minusMinutes(35), true),
                new AdminWaitingRow(5L, "010-****-6350", "WAITING", "대기 중", 4, now.minusMinutes(31), true),
                new AdminWaitingRow(6L, "010-****-0826", "WAITING", "대기 중", 5, now.minusMinutes(28), true),
                new AdminWaitingRow(7L, "010-****-5720", "WAITING", "대기 중", 6, now.minusMinutes(24), true),
                new AdminWaitingRow(8L, "010-****-3349", "WAITING", "대기 중", 7, now.minusMinutes(20), true),
                new AdminWaitingRow(9L, "010-****-7811", "WAITING", "대기 중", 8, now.minusMinutes(17), true),
                new AdminWaitingRow(10L, "010-****-4098", "WAITING", "대기 중", 9, now.minusMinutes(13), true),
                new AdminWaitingRow(11L, "010-****-2604", "WAITING", "대기 중", 10, now.minusMinutes(9), true),
                new AdminWaitingRow(12L, "010-****-9972", "WAITING", "대기 중", 11, now.minusMinutes(5), true),
                new AdminWaitingRow(13L, "010-****-1234", "WAITING", "대기 중", 12, now.minusMinutes(2), true)
        );
    }

    private List<AdminEnteredRow> enteredWaitingRows() {
        LocalDateTime now = LocalDateTime.now();
        return IntStream.rangeClosed(1, 12)
                .mapToObj(index -> new AdminEnteredRow(
                        100L + index,
                        maskedPhoneNumber(index),
                        "ENTERED",
                        "입장 완료",
                        now.minusMinutes(110L - index * 4L),
                        now.minusMinutes(86L - index * 4L)
                ))
                .toList();
    }

    private String maskedPhoneNumber(int index) {
        return "010-****-" + String.format("%04d", 2300 + index * 137);
    }

    private long countByStatus(List<AdminWaitingRow> waitings, String status) {
        return waitings.stream()
                .filter(waiting -> waiting.status().equals(status))
                .count();
    }

    private <T> PageView<T> paginate(List<T> items, int requestedPage) {
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / PAGE_SIZE));
        int currentPage = Math.min(Math.max(requestedPage, 1), totalPages);
        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, items.size());
        List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages)
                .boxed()
                .toList();

        return new PageView<>(
                items.subList(fromIndex, toIndex),
                currentPage,
                totalPages,
                items.size(),
                PAGE_SIZE,
                currentPage > 1,
                currentPage < totalPages,
                Math.max(1, currentPage - 1),
                Math.min(totalPages, currentPage + 1),
                pageNumbers
        );
    }

    public record WaitingStatusView(
            Long id,
            String maskedPhoneNumber,
            String status,
            String statusLabel,
            Integer remainingCount,
            LocalDateTime createdAt,
            String notice,
            String title,
            boolean cancellable
    ) {
    }

    public record AdminSummary(
            int total,
            long waiting,
            long called,
            int totalPages
    ) {
    }

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

    public record AdminEnteredRow(
            Long id,
            String maskedPhoneNumber,
            String status,
            String statusLabel,
            LocalDateTime createdAt,
            LocalDateTime enteredAt
    ) {
    }

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
}
