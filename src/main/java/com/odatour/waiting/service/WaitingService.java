package com.odatour.waiting.service;

import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import com.odatour.waiting.notification.WaitingNotificationFailedException;
import com.odatour.waiting.notification.WaitingNotificationSender;
import com.odatour.waiting.repository.WaitingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitingService {

    private static final Logger log = LoggerFactory.getLogger(WaitingService.class);
    private static final int BOOTH_QUEUE_CAPACITY = 5;
    private static final Duration CALL_OVERDUE_DURATION = Duration.ofMinutes(10);
    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(
            WaitingStatus.WAITING,
            WaitingStatus.CALLED,
            WaitingStatus.ARRIVED
    );
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^(010\\d{8}|010-\\d{4}-\\d{4})$");

    private final WaitingRepository waitingRepository;
    private final WaitingNotificationSender waitingNotificationSender;
    private final Clock clock;

    public WaitingService(WaitingRepository waitingRepository, WaitingNotificationSender waitingNotificationSender,
                          Clock clock) {
        this.waitingRepository = waitingRepository;
        this.waitingNotificationSender = waitingNotificationSender;
        this.clock = clock;
    }

    @Transactional
    public WaitingEntry createWaiting(String rawPhoneNumber, boolean consentAgreed) {
        String trimmedPhoneNumber = rawPhoneNumber == null ? "" : rawPhoneNumber.trim();
        if (trimmedPhoneNumber.isBlank()) {
            throw new IllegalArgumentException("휴대폰 번호를 입력해 주세요.");
        }
        if (!PHONE_NUMBER_PATTERN.matcher(trimmedPhoneNumber).matches()) {
            throw new IllegalArgumentException("휴대폰 번호 형식이 올바르지 않습니다.");
        }
        if (!consentAgreed) {
            throw new IllegalArgumentException("개인정보 수집 및 이용에 동의해 주세요.");
        }

        String phoneNumber = normalizePhoneNumber(trimmedPhoneNumber);
        Optional<WaitingEntry> activeWaiting = waitingRepository.findFirstByPhoneNumberAndStatuses(
                phoneNumber,
                ACTIVE_STATUSES
        );
        if (activeWaiting.isPresent()) {
            throw new DuplicateActiveWaitingException(activeWaiting.get());
        }

        return waitingRepository.save(phoneNumber, true, WaitingStatus.WAITING, now());
    }

    @Transactional(readOnly = true)
    public WaitingEntry findWaiting(Long id) {
        return waitingRepository.findById(id)
                .orElseThrow(() -> new WaitingNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<WaitingEntry> activeWaitings() {
        return waitingRepository.findByStatuses(ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<WaitingEntry> enteredWaitings() {
        return waitingRepository.findEntered();
    }

    @Transactional(readOnly = true)
    public List<WaitingEntry> completedWaitings() {
        return waitingRepository.findCompleted();
    }

    @Transactional
    public void cancelWaiting(Long id) {
        waitingRepository.updateStatus(id, List.of(WaitingStatus.WAITING), WaitingStatus.CANCELED, now());
    }

    @Transactional
    public void adminCancelWaiting(Long id) {
        waitingRepository.updateStatus(id, List.of(WaitingStatus.WAITING), WaitingStatus.CANCELED, now());
    }

    @Transactional
    public void arriveWaiting(Long id) {
        waitingRepository.updateStatus(id, List.of(WaitingStatus.CALLED), WaitingStatus.ARRIVED, now());
    }

    @Transactional
    public void enterWaiting(Long id) {
        waitingRepository.updateStatus(id, List.of(WaitingStatus.ARRIVED), WaitingStatus.ENTERED, now());
    }

    @Transactional
    public void noShowWaiting(Long id) {
        WaitingEntry waiting = waitingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new WaitingNotFoundException(id));
        if (waiting.status() == WaitingStatus.CALLED || waiting.status() == WaitingStatus.ARRIVED) {
            waitingRepository.updateStatus(
                    id,
                    List.of(WaitingStatus.CALLED, WaitingStatus.ARRIVED),
                    WaitingStatus.NO_SHOWED,
                    now()
            );
        }
    }

    @Transactional
    public void revertAdminWaiting(Long id) {
        WaitingEntry waiting = waitingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new WaitingNotFoundException(id));

        WaitingStatus previousStatus = switch (waiting.status()) {
            case CALLED -> WaitingStatus.WAITING;
            case ARRIVED -> WaitingStatus.CALLED;
            case ENTERED -> WaitingStatus.ARRIVED;
            case NO_SHOWED -> WaitingStatus.CALLED;
            case WAITING, CANCELED -> null;
        };

        if (previousStatus != null) {
            waitingRepository.revertStatus(id, waiting.status(), previousStatus, now());
        }
    }

    @Transactional
    public boolean notifyWaiting(Long id) {
        WaitingEntry waiting = waitingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new WaitingNotFoundException(id));
        if (waiting.status() != WaitingStatus.WAITING || waiting.notifiedAt() != null) {
            return false;
        }

        try {
            waitingNotificationSender.sendCall(waiting);
            return waitingRepository.markNotified(id, now()) == 1;
        } catch (RuntimeException exception) {
            log.error("카카오 알림톡 발송 실패. waitingId={}, phoneNumber={}", waiting.id(), waiting.phoneNumber(), exception);
            throw exception instanceof WaitingNotificationFailedException
                    ? exception
                    : new WaitingNotificationFailedException(id, exception);
        }
    }

    @Transactional
    public int notifyShortageWaitings() {
        int shortageCount = callShortageCount(activeWaitings());
        if (shortageCount == 0) {
            return 0;
        }

        int sentCount = 0;
        for (WaitingEntry waiting : activeWaitings()) {
            if (waiting.status() == WaitingStatus.WAITING && sentCount < shortageCount && notifyWaiting(waiting.id())) {
                sentCount++;
            }
        }
        return sentCount;
    }

    public int boothQueueCapacity() {
        return BOOTH_QUEUE_CAPACITY;
    }

    public long boothQueueCount(List<WaitingEntry> activeWaitings) {
        return activeWaitings.stream()
                .filter(waiting -> waiting.status() == WaitingStatus.CALLED || waiting.status() == WaitingStatus.ARRIVED)
                .count();
    }

    public int callShortageCount(List<WaitingEntry> activeWaitings) {
        long boothQueueCount = boothQueueCount(activeWaitings);
        return (int) Math.max(BOOTH_QUEUE_CAPACITY - boothQueueCount, 0);
    }

    public boolean callOverdue(WaitingEntry waiting) {
        return waiting.status() == WaitingStatus.CALLED
                && waiting.notifiedAt() != null
                && !waiting.notifiedAt().plus(CALL_OVERDUE_DURATION).isAfter(now());
    }

    public String callElapsedLabel(WaitingEntry waiting) {
        if (waiting.status() != WaitingStatus.CALLED || waiting.notifiedAt() == null) {
            return null;
        }

        long elapsedMinutes = Math.max(Duration.between(waiting.notifiedAt(), now()).toMinutes(), 0);
        long hours = elapsedMinutes / 60;
        long minutes = elapsedMinutes % 60;
        if (hours == 0) {
            return minutes + "분 경과";
        }
        if (minutes == 0) {
            return hours + "시간 경과";
        }
        return hours + "시간 " + minutes + "분 경과";
    }

    public int remainingCount(WaitingEntry waiting, List<WaitingEntry> activeWaitings) {
        if (!waiting.status().active()) {
            return 0;
        }

        int count = 0;
        for (WaitingEntry activeWaiting : activeWaitings) {
            if (activeWaiting.id().equals(waiting.id())) {
                return count;
            }
            count++;
        }
        return count;
    }

    public String normalizePhoneNumber(String rawPhoneNumber) {
        if (rawPhoneNumber == null) {
            return "";
        }
        return rawPhoneNumber.replaceAll("\\D", "");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
