package com.odatour.waiting.service;

import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import com.odatour.waiting.repository.WaitingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitingService {

    private static final List<WaitingStatus> ACTIVE_STATUSES = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("^(010\\d{8}|010-\\d{4}-\\d{4})$");

    private final WaitingRepository waitingRepository;
    private final Clock clock;

    public WaitingService(WaitingRepository waitingRepository, Clock clock) {
        this.waitingRepository = waitingRepository;
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

    @Transactional
    public void cancelWaiting(Long id) {
        waitingRepository.updateStatus(id, WaitingStatus.WAITING, WaitingStatus.CALLED, WaitingStatus.CANCELED, now());
    }

    @Transactional
    public void enterWaiting(Long id) {
        waitingRepository.updateStatus(id, WaitingStatus.WAITING, WaitingStatus.CALLED, WaitingStatus.ENTERED, now());
    }

    @Transactional
    public void noShowWaiting(Long id) {
        waitingRepository.updateStatus(id, WaitingStatus.WAITING, WaitingStatus.CALLED, WaitingStatus.NO_SHOW, now());
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
