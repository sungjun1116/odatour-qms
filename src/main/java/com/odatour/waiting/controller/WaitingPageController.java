package com.odatour.waiting.controller;

import com.odatour.waiting.controller.view.AdminCompletedRow;
import com.odatour.waiting.controller.view.AdminSummary;
import com.odatour.waiting.controller.view.AdminWaitingRow;
import com.odatour.waiting.controller.view.PageView;
import com.odatour.waiting.controller.view.WaitingStatusView;
import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import com.odatour.waiting.notification.WaitingNotificationFailedException;
import com.odatour.waiting.service.DuplicateActiveWaitingException;
import com.odatour.waiting.service.WaitingService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WaitingPageController {

    private static final int PAGE_SIZE = 10;
    private static final int ESTIMATED_WAIT_MINUTES_PER_TEAM = 3;

    private final WaitingService waitingService;

    public WaitingPageController(WaitingService waitingService) {
        this.waitingService = waitingService;
    }

    @GetMapping("/")
    public String newWaiting(Model model) {
        if (!model.containsAttribute("phoneNumber")) {
            model.addAttribute("phoneNumber", "");
        }
        addNewWaitingMetrics(model);
        return "waitings/new";
    }

    @PostMapping("/waitings")
    public String createWaiting(
            @RequestParam String phoneNumber,
            @RequestParam(defaultValue = "false") boolean consentAgreed,
            Model model
    ) {
        try {
            WaitingEntry waiting = waitingService.createWaiting(phoneNumber, consentAgreed);
            return "redirect:/waitings/" + waiting.id();
        } catch (DuplicateActiveWaitingException exception) {
            model.addAttribute("errorMessage", "이미 웨이팅 중인 휴대폰 번호입니다.");
            model.addAttribute("activeWaitingId", exception.activeWaiting().id());
            model.addAttribute("phoneNumber", phoneNumber);
            addNewWaitingMetrics(model);
            return "waitings/new";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("phoneNumber", phoneNumber);
            addNewWaitingMetrics(model);
            return "waitings/new";
        }
    }

    @GetMapping("/waitings/{id}")
    public String waitingStatus(@PathVariable Long id, Model model) {
        WaitingEntry waiting = waitingService.findWaiting(id);
        List<WaitingEntry> activeWaitings = waitingService.activeWaitings();
        model.addAttribute("waiting", waitingStatusView(waiting, activeWaitings));
        return "waitings/status";
    }

    @PostMapping("/waitings/{id}/cancel")
    public String cancelWaiting(@PathVariable Long id) {
        waitingService.cancelWaiting(id);
        return "redirect:/waitings/" + id;
    }

    @GetMapping("/admin/waitings")
    public String adminWaitings(@RequestParam(defaultValue = "1") int page, Model model) {
        List<WaitingEntry> activeWaitings = waitingService.activeWaitings();
        List<AdminWaitingRow> activeWaitingRows = groupedActiveWaitingRows(activeWaitings);
        PageView<AdminWaitingRow> pageView = paginate(activeWaitingRows, page);

        model.addAttribute("summary", new AdminSummary(
                activeWaitingRows.size(),
                countWaitingRowsByStatus(activeWaitingRows, WaitingStatus.WAITING.name()),
                countWaitingRowsByStatus(activeWaitingRows, WaitingStatus.CALLED.name()),
                countWaitingRowsByStatus(activeWaitingRows, WaitingStatus.ARRIVED.name()),
                pageView.totalPages()
        ));
        model.addAttribute("waitings", pageView.items());
        model.addAttribute("sectionHeadings", sectionHeadings(pageView.items()));
        model.addAttribute("page", pageView);
        model.addAttribute("completedCount", waitingService.completedWaitings().size());
        model.addAttribute("boothQueueCapacity", waitingService.boothQueueCapacity());
        model.addAttribute("boothQueueCount", waitingService.boothQueueCount(activeWaitings));
        model.addAttribute("callShortageCount", waitingService.callShortageCount(activeWaitings));
        return "admin/waitings";
    }

    @GetMapping("/admin/waitings/entered")
    public String enteredWaitings(@RequestParam(defaultValue = "1") int page, Model model) {
        List<AdminCompletedRow> completedWaitings = completedWaitingRows();
        PageView<AdminCompletedRow> pageView = paginate(completedWaitings, page);

        model.addAttribute("completedWaitings", pageView.items());
        model.addAttribute("page", pageView);
        model.addAttribute("activeCount", waitingService.activeWaitings().size());
        model.addAttribute("enteredCount", countCompletedRowsByStatus(completedWaitings, WaitingStatus.ENTERED.name()));
        model.addAttribute("noShowedCount", countCompletedRowsByStatus(completedWaitings, WaitingStatus.NO_SHOWED.name()));
        return "admin/entered-waitings";
    }

    @PostMapping("/admin/waitings/{id}/enter")
    public String enter(@PathVariable Long id) {
        waitingService.enterWaiting(id);
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/arrive")
    public String arrive(@PathVariable Long id) {
        waitingService.arriveWaiting(id);
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/cancel")
    public String adminCancel(@PathVariable Long id) {
        waitingService.adminCancelWaiting(id);
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/no-show")
    public String noShow(@PathVariable Long id) {
        waitingService.noShowWaiting(id);
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/revert")
    public String revert(@PathVariable Long id) {
        waitingService.revertAdminWaiting(id);
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/notify-shortage")
    public String notifyShortage(RedirectAttributes redirectAttributes) {
        try {
            int sentCount = waitingService.notifyShortageWaitings();
            redirectAttributes.addFlashAttribute(
                    sentCount > 0 ? "successMessage" : "errorMessage",
                    sentCount > 0
                            ? sentCount + "명에게 카카오 알림톡을 발송했습니다."
                            : "호출할 부족 인원이 없거나 호출 가능한 웨이팅이 없습니다."
            );
        } catch (WaitingNotificationFailedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "카카오 알림톡 발송에 실패했습니다. 로그를 확인해 주세요.");
        }
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/notify")
    public String notify(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            boolean sent = waitingService.notifyWaiting(id);
            redirectAttributes.addFlashAttribute(
                    sent ? "successMessage" : "errorMessage",
                    sent ? "카카오 알림톡을 발송했습니다." : "이미 호출되었거나 발송할 수 없는 웨이팅입니다."
            );
        } catch (WaitingNotificationFailedException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "카카오 알림톡 발송에 실패했습니다. 로그를 확인해 주세요.");
        }
        return "redirect:/admin/waitings";
    }

    private WaitingStatusView waitingStatusView(WaitingEntry waiting, List<WaitingEntry> activeWaitings) {
        Integer remainingCount = waiting.status().active()
                ? waitingService.remainingCount(waiting, activeWaitings)
                : null;

        return new WaitingStatusView(
                waiting.id(),
                formatPhoneNumber(waiting.phoneNumber()),
                waiting.status().name(),
                waiting.status().label(),
                remainingCount,
                estimatedWaitMinutes(remainingCount),
                waiting.createdAt(),
                notice(waiting.status(), remainingCount),
                title(waiting.status(), remainingCount),
                waiting.status().cancellable()
        );
    }

    private List<AdminWaitingRow> activeWaitingRows(List<WaitingEntry> activeWaitings) {
        List<AdminWaitingRow> rows = new ArrayList<>();

        for (int index = 0; index < activeWaitings.size(); index++) {
            WaitingEntry waiting = activeWaitings.get(index);

            rows.add(new AdminWaitingRow(
                    waiting.id(),
                    formatPhoneNumber(waiting.phoneNumber()),
                    waiting.status().name(),
                    waiting.status().label(),
                    index,
                    waiting.createdAt(),
                    waiting.notifiedAt(),
                    waitingService.callElapsedLabel(waiting),
                    waitingService.callOverdue(waiting),
                    waiting.status() == WaitingStatus.WAITING && waiting.notifiedAt() == null,
                    waiting.status() == WaitingStatus.CALLED,
                    waiting.status() == WaitingStatus.ARRIVED,
                    waiting.status() == WaitingStatus.CALLED || waiting.status() == WaitingStatus.ARRIVED,
                    waiting.status() == WaitingStatus.WAITING,
                    waiting.status() == WaitingStatus.CALLED || waiting.status() == WaitingStatus.ARRIVED,
                    waiting.status().active()
            ));
        }

        return rows;
    }

    private List<AdminWaitingRow> groupedActiveWaitingRows(List<WaitingEntry> activeWaitings) {
        return activeWaitingRows(activeWaitings).stream()
                .sorted(Comparator.comparingInt(this::statusGroupOrder)
                        .thenComparing(AdminWaitingRow::remainingCount))
                .toList();
    }

    private Map<Long, String> sectionHeadings(List<AdminWaitingRow> waitings) {
        Map<Long, String> headings = new LinkedHashMap<>();
        String previousStatus = null;
        for (AdminWaitingRow waiting : waitings) {
            if (!waiting.status().equals(previousStatus)) {
                headings.put(waiting.id(), sectionLabel(waiting.status()));
                previousStatus = waiting.status();
            }
        }
        return headings;
    }

    private int statusGroupOrder(AdminWaitingRow waiting) {
        return switch (WaitingStatus.valueOf(waiting.status())) {
            case WAITING -> 0;
            case CALLED -> 1;
            case ARRIVED -> 2;
            case ENTERED, NO_SHOWED, CANCELED -> 3;
        };
    }

    private String sectionLabel(String status) {
        return switch (WaitingStatus.valueOf(status)) {
            case WAITING -> "WAITING 고객";
            case CALLED -> "CALLED 고객";
            case ARRIVED -> "ARRIVED 고객";
            case ENTERED -> "ENTERED 고객";
            case NO_SHOWED -> "NO_SHOW 고객";
            case CANCELED -> "CANCELED 고객";
        };
    }

    private List<AdminCompletedRow> completedWaitingRows() {
        return waitingService.completedWaitings().stream()
                .map(waiting -> new AdminCompletedRow(
                        waiting.id(),
                        formatPhoneNumber(waiting.phoneNumber()),
                        waiting.status().name(),
                        waiting.status().label(),
                        waiting.createdAt(),
                        processedAt(waiting),
                        waiting.status() == WaitingStatus.ENTERED || waiting.status() == WaitingStatus.NO_SHOWED
                ))
                .toList();
    }

    private LocalDateTime processedAt(WaitingEntry waiting) {
        return switch (waiting.status()) {
            case ENTERED -> waiting.enteredAt() == null ? waiting.updatedAt() : waiting.enteredAt();
            case NO_SHOWED -> waiting.noShowAt() == null ? waiting.updatedAt() : waiting.noShowAt();
            case WAITING, CALLED, ARRIVED, CANCELED -> waiting.updatedAt();
        };
    }

    private String title(WaitingStatus status, Integer remainingCount) {
        return switch (status) {
            case WAITING -> remainingCount == 0 ? "곧 입장 차례입니다" : remainingCount + "팀 앞에서 대기 중입니다";
            case CALLED -> "부스로 이동해 주세요";
            case ARRIVED -> "현장 도착이 확인되었습니다";
            case ENTERED -> "입장 완료되었습니다";
            case NO_SHOWED -> "노쇼 처리되었습니다";
            case CANCELED -> "웨이팅이 취소되었습니다";
        };
    }

    private String notice(WaitingStatus status, Integer remainingCount) {
        return switch (status) {
            case WAITING -> remainingCount == 0
                    ? "현장 직원 안내에 따라 입장을 준비해 주세요."
                    : "앞 순서가 가까워지면 카카오 알림톡으로 알려드립니다.";
            case CALLED -> "호출 카카오 알림톡을 발송했습니다. 부스 대기줄에 도착하면 현장 직원에게 확인해 주세요.";
            case ARRIVED -> "현장 직원이 도착을 확인했습니다. 체험 시작 전까지 대기해 주세요.";
            case ENTERED -> "입장 처리가 완료되었습니다.";
            case NO_SHOWED -> "호출 후 10분 동안 현장 도착이 확인되지 않아 노쇼 처리되었습니다.";
            case CANCELED -> "웨이팅 취소가 완료되었습니다.";
        };
    }

    private int estimatedWaitMinutes(Integer teamCount) {
        if (teamCount == null) {
            return 0;
        }
        return Math.max(teamCount, 0) * ESTIMATED_WAIT_MINUTES_PER_TEAM;
    }

    private void addNewWaitingMetrics(Model model) {
        int estimatedWaitTeams = waitingService.activeWaitings().size();
        model.addAttribute("estimatedWaitTeams", estimatedWaitTeams);
        model.addAttribute("estimatedWaitMinutes", estimatedWaitMinutes(estimatedWaitTeams));
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() != 11) {
            return phoneNumber;
        }
        return phoneNumber.substring(0, 3)
                + "-"
                + phoneNumber.substring(3, 7)
                + "-"
                + phoneNumber.substring(7);
    }

    private long countWaitingRowsByStatus(List<AdminWaitingRow> waitings, String status) {
        return waitings.stream()
                .filter(waiting -> waiting.status().equals(status))
                .count();
    }

    private long countCompletedRowsByStatus(List<AdminCompletedRow> waitings, String status) {
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

}
