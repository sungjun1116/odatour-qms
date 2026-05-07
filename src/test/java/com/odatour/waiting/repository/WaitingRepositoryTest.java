package com.odatour.waiting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingRepositoryTest {

    @Autowired
    private WaitingRepository waitingRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("delete from waiting_entry").update();
    }

    @Test
    void savePersistsWaitingEntry() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 10, 0);

        WaitingEntry saved = waitingRepository.save("01012345678", true, WaitingStatus.WAITING, now);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.phoneNumber()).isEqualTo("01012345678");
        assertThat(saved.consentAgreed()).isTrue();
        assertThat(saved.status()).isEqualTo(WaitingStatus.WAITING);
        assertThat(saved.createdAt()).isEqualTo(now);
        assertThat(saved.updatedAt()).isEqualTo(now);
    }

    @Test
    void findByStatusesReturnsEntriesByCreatedAtAndId() {
        LocalDateTime first = LocalDateTime.of(2026, 5, 8, 10, 0);
        LocalDateTime second = first.plusMinutes(1);

        WaitingEntry waiting = waitingRepository.save("01012345678", true, WaitingStatus.WAITING, second);
        WaitingEntry called = waitingRepository.save("01012345679", true, WaitingStatus.CALLED, first);
        waitingRepository.save("01012345670", true, WaitingStatus.ENTERED, first.plusMinutes(2));

        List<WaitingEntry> activeWaitings = waitingRepository.findByStatuses(
                List.of(WaitingStatus.WAITING, WaitingStatus.CALLED)
        );

        assertThat(activeWaitings)
                .extracting(WaitingEntry::id)
                .containsExactly(called.id(), waiting.id());
    }

    @Test
    void findFirstByPhoneNumberAndStatusesFindsOnlyActiveWaiting() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 8, 10, 0);
        waitingRepository.save("01012345678", true, WaitingStatus.ENTERED, now);
        WaitingEntry active = waitingRepository.save("01012345678", true, WaitingStatus.WAITING, now.plusMinutes(1));

        assertThat(waitingRepository.findFirstByPhoneNumberAndStatuses(
                "01012345678",
                List.of(WaitingStatus.WAITING, WaitingStatus.CALLED)
        )).hasValueSatisfying(waiting -> assertThat(waiting.id()).isEqualTo(active.id()));
    }

    @Test
    void updateStatusChangesStatusAndEventTimestampForExpectedStatusesOnly() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 8, 10, 0);
        LocalDateTime enteredAt = createdAt.plusMinutes(5);
        WaitingEntry waiting = waitingRepository.save("01012345678", true, WaitingStatus.WAITING, createdAt);
        WaitingEntry alreadyEntered = waitingRepository.save("01012345679", true, WaitingStatus.ENTERED, createdAt);

        int updated = waitingRepository.updateStatus(
                waiting.id(),
                WaitingStatus.WAITING,
                WaitingStatus.CALLED,
                WaitingStatus.ENTERED,
                enteredAt
        );
        int ignored = waitingRepository.updateStatus(
                alreadyEntered.id(),
                WaitingStatus.WAITING,
                WaitingStatus.CALLED,
                WaitingStatus.CANCELED,
                enteredAt
        );

        assertThat(updated).isEqualTo(1);
        assertThat(ignored).isZero();
        assertThat(waitingRepository.findById(waiting.id()))
                .hasValueSatisfying(updatedWaiting -> {
                    assertThat(updatedWaiting.status()).isEqualTo(WaitingStatus.ENTERED);
                    assertThat(updatedWaiting.enteredAt()).isEqualTo(enteredAt);
                });
    }

    @Test
    void findEnteredReturnsLatestEnteredFirst() {
        LocalDateTime base = LocalDateTime.of(2026, 5, 8, 10, 0);
        WaitingEntry firstEntered = waitingRepository.save("01012345678", true, WaitingStatus.WAITING, base);
        WaitingEntry secondEntered = waitingRepository.save("01012345679", true, WaitingStatus.WAITING, base.plusMinutes(1));
        waitingRepository.save("01012345670", true, WaitingStatus.WAITING, base.plusMinutes(2));

        waitingRepository.updateStatus(
                firstEntered.id(),
                WaitingStatus.WAITING,
                WaitingStatus.CALLED,
                WaitingStatus.ENTERED,
                base.plusMinutes(10)
        );
        waitingRepository.updateStatus(
                secondEntered.id(),
                WaitingStatus.WAITING,
                WaitingStatus.CALLED,
                WaitingStatus.ENTERED,
                base.plusMinutes(20)
        );

        assertThat(waitingRepository.findEntered())
                .extracting(WaitingEntry::id)
                .containsExactly(secondEntered.id(), firstEntered.id());
    }
}
