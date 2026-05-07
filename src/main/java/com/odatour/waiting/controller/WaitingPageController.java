package com.odatour.waiting.controller;

import com.odatour.waiting.controller.view.AdminEnteredRow;
import com.odatour.waiting.controller.view.AdminSummary;
import com.odatour.waiting.controller.view.AdminWaitingRow;
import com.odatour.waiting.controller.view.PageView;
import com.odatour.waiting.controller.view.WaitingStatusView;
import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import com.odatour.waiting.service.DuplicateActiveWaitingException;
import com.odatour.waiting.service.WaitingService;
import java.util.List;
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
        model.addAttribute("estimatedWaitMinutes", estimatedWaitMinutes(waitingService.activeWaitings().size()));
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
            model.addAttribute("estimatedWaitMinutes", estimatedWaitMinutes(waitingService.activeWaitings().size()));
            return "waitings/new";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("phoneNumber", phoneNumber);
            model.addAttribute("estimatedWaitMinutes", estimatedWaitMinutes(waitingService.activeWaitings().size()));
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
        List<AdminWaitingRow> activeWaitingRows = activeWaitingRows(activeWaitings);
        PageView<AdminWaitingRow> pageView = paginate(activeWaitingRows, page);

        model.addAttribute("summary", new AdminSummary(
                activeWaitingRows.size(),
                countByStatus(activeWaitingRows, WaitingStatus.WAITING.name()),
                countByStatus(activeWaitingRows, WaitingStatus.CALLED.name()),
                pageView.totalPages()
        ));
        model.addAttribute("waitings", pageView.items());
        model.addAttribute("page", pageView);
        model.addAttribute("enteredCount", waitingService.enteredWaitings().size());
        return "admin/waitings";
    }

    @GetMapping("/admin/waitings/entered")
    public String enteredWaitings(@RequestParam(defaultValue = "1") int page, Model model) {
        PageView<AdminEnteredRow> pageView = paginate(enteredWaitingRows(), page);

        model.addAttribute("enteredWaitings", pageView.items());
        model.addAttribute("page", pageView);
        model.addAttribute("activeCount", waitingService.activeWaitings().size());
        return "admin/entered-waitings";
    }

    @PostMapping("/admin/waitings/{id}/enter")
    public String enter(@PathVariable Long id) {
        waitingService.enterWaiting(id);
        return "redirect:/admin/waitings";
    }

    @PostMapping("/admin/waitings/{id}/no-show")
    public String noShow(@PathVariable Long id) {
        waitingService.noShowWaiting(id);
        return "redirect:/admin/waitings";
    }

    private WaitingStatusView waitingStatusView(WaitingEntry waiting, List<WaitingEntry> activeWaitings) {
        Integer remainingCount = waiting.status().active()
                ? waitingService.remainingCount(waiting, activeWaitings)
                : null;

        return new WaitingStatusView(
                waiting.id(),
                maskPhoneNumber(waiting.phoneNumber()),
                waiting.status().name(),
                waiting.status().label(),
                remainingCount,
                estimatedWaitMinutes(remainingCount),
                waiting.createdAt(),
                notice(waiting.status(), remainingCount),
                title(waiting.status(), remainingCount),
                waiting.status().active()
        );
    }

    private List<AdminWaitingRow> activeWaitingRows(List<WaitingEntry> activeWaitings) {
        return IntStream.range(0, activeWaitings.size())
                .mapToObj(index -> {
                    WaitingEntry waiting = activeWaitings.get(index);
                    return new AdminWaitingRow(
                            waiting.id(),
                            maskPhoneNumber(waiting.phoneNumber()),
                            waiting.status().name(),
                            waiting.status().label(),
                            index,
                            waiting.createdAt(),
                            waiting.status().active()
                    );
                })
                .toList();
    }

    private List<AdminEnteredRow> enteredWaitingRows() {
        return waitingService.enteredWaitings().stream()
                .map(waiting -> new AdminEnteredRow(
                        waiting.id(),
                        maskPhoneNumber(waiting.phoneNumber()),
                        waiting.status().name(),
                        waiting.status().label(),
                        waiting.createdAt(),
                        waiting.enteredAt()
                ))
                .toList();
    }

    private String title(WaitingStatus status, Integer remainingCount) {
        return switch (status) {
            case WAITING -> remainingCount == 0 ? "곧 입장 차례입니다" : remainingCount + "팀 앞에서 대기 중입니다";
            case CALLED -> "입장 준비가 완료되었습니다";
            case ENTERED -> "입장 완료되었습니다";
            case NO_SHOW -> "노쇼 처리되었습니다";
            case CANCELED -> "웨이팅이 취소되었습니다";
        };
    }

    private String notice(WaitingStatus status, Integer remainingCount) {
        return switch (status) {
            case WAITING -> remainingCount == 0
                    ? "현장 직원 안내에 따라 입장을 준비해 주세요."
                    : "앞 순서가 가까워지면 SMS로 알려드립니다.";
            case CALLED -> "호출 SMS를 발송했습니다. 현장 직원 안내에 따라 입장해 주세요.";
            case ENTERED -> "입장 처리가 완료되었습니다.";
            case NO_SHOW -> "현장 관리자에 의해 노쇼 처리되었습니다.";
            case CANCELED -> "웨이팅 취소가 완료되었습니다.";
        };
    }

    private int estimatedWaitMinutes(Integer teamCount) {
        if (teamCount == null) {
            return 0;
        }
        return Math.max(teamCount, 0) * ESTIMATED_WAIT_MINUTES_PER_TEAM;
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "****";
        }
        String prefix = phoneNumber.substring(0, Math.min(3, phoneNumber.length()));
        String suffix = phoneNumber.substring(phoneNumber.length() - 4);
        return prefix + "-****-" + suffix;
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

}
