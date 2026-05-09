package com.odatour.waiting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import com.odatour.waiting.notification.WaitingNotificationFailedException;
import com.odatour.waiting.notification.WaitingNotificationSender;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingServiceTest {

    @Autowired
    private WaitingService waitingService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TestWaitingNotificationSender waitingNotificationSender;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("delete from waiting_entry").update();
        waitingNotificationSender.clear();
    }

    @Test
    void createWaitingNormalizesAndPersistsPhoneNumber() {
        WaitingEntry waiting = waitingService.createWaiting("010-1234-5678", true);

        assertThat(waiting.phoneNumber()).isEqualTo("01012345678");
        assertThat(waiting.status()).isEqualTo(WaitingStatus.WAITING);
        assertThat(waiting.consentAgreed()).isTrue();
    }

    @Test
    void createWaitingRejectsDuplicateActivePhoneNumber() {
        WaitingEntry activeWaiting = waitingService.createWaiting("01012345678", true);

        assertThatThrownBy(() -> waitingService.createWaiting("010-1234-5678", true))
                .isInstanceOf(DuplicateActiveWaitingException.class)
                .extracting(exception -> ((DuplicateActiveWaitingException) exception).activeWaiting().id())
                .isEqualTo(activeWaiting.id());
    }

    @Test
    void createWaitingRejectsInvalidPhoneNumber() {
        assertThatThrownBy(() -> waitingService.createWaiting("010-123-5678", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("휴대폰 번호 형식이 올바르지 않습니다.");

        Integer count = jdbcClient.sql("select count(*) from waiting_entry")
                .query(Integer.class)
                .single();
        assertThat(count).isZero();
    }

    @Test
    void createWaitingRejectsMissingConsent() {
        assertThatThrownBy(() -> waitingService.createWaiting("01012345678", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("개인정보 수집 및 이용에 동의해 주세요.");
    }

    @Test
    void canceledPhoneNumberCanRegisterAgain() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);

        waitingService.cancelWaiting(waiting.id());
        WaitingEntry registeredAgain = waitingService.createWaiting("010-1234-5678", true);

        assertThat(registeredAgain.id()).isNotEqualTo(waiting.id());
        assertThat(waitingService.findWaiting(waiting.id()).status()).isEqualTo(WaitingStatus.CANCELED);
    }

    @Test
    void adminCancelWaitingCancelsOnlyWaitingStatus() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        WaitingEntry called = waitingService.createWaiting("01012345679", true);
        WaitingEntry arrived = waitingService.createWaiting("01012345670", true);

        waitingService.notifyWaiting(called.id());
        waitingService.notifyWaiting(arrived.id());
        waitingService.arriveWaiting(arrived.id());

        waitingService.adminCancelWaiting(waiting.id());
        waitingService.adminCancelWaiting(called.id());
        waitingService.adminCancelWaiting(arrived.id());

        assertThat(waitingService.findWaiting(waiting.id()).status()).isEqualTo(WaitingStatus.CANCELED);
        assertThat(waitingService.findWaiting(called.id()).status()).isEqualTo(WaitingStatus.CALLED);
        assertThat(waitingService.findWaiting(arrived.id()).status()).isEqualTo(WaitingStatus.ARRIVED);
    }

    @Test
    void activeWaitingsReturnsWaitingCalledAndArrivedOnly() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        WaitingEntry called = waitingService.createWaiting("01012345679", true);
        WaitingEntry arrived = waitingService.createWaiting("01012345670", true);
        WaitingEntry entered = waitingService.createWaiting("01012345671", true);
        WaitingEntry noShow = waitingService.createWaiting("01012345672", true);

        waitingService.notifyWaiting(called.id());
        waitingService.notifyWaiting(arrived.id());
        waitingService.arriveWaiting(arrived.id());
        waitingService.notifyWaiting(entered.id());
        waitingService.arriveWaiting(entered.id());
        waitingService.enterWaiting(entered.id());
        waitingService.notifyWaiting(noShow.id());
        makeCallOverdue(noShow.id());
        waitingService.noShowWaiting(noShow.id());

        assertThat(waitingService.activeWaitings())
                .extracting(WaitingEntry::id)
                .containsExactly(waiting.id(), called.id(), arrived.id());
    }

    @Test
    void remainingCountReturnsNumberOfActiveWaitingsBeforeTarget() {
        WaitingEntry first = waitingService.createWaiting("01012345678", true);
        WaitingEntry second = waitingService.createWaiting("01012345679", true);
        WaitingEntry third = waitingService.createWaiting("01012345670", true);
        waitingService.notifyWaiting(first.id());
        waitingService.arriveWaiting(first.id());
        waitingService.enterWaiting(first.id());

        List<WaitingEntry> activeWaitings = waitingService.activeWaitings();

        assertThat(waitingService.remainingCount(second, activeWaitings)).isZero();
        assertThat(waitingService.remainingCount(third, activeWaitings)).isEqualTo(1);
        assertThat(waitingService.remainingCount(waitingService.findWaiting(first.id()), activeWaitings)).isZero();
    }

    @Test
    void arriveWaitingChangesCalledWaitingToArrived() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        waitingService.notifyWaiting(waiting.id());

        waitingService.arriveWaiting(waiting.id());

        WaitingEntry arrived = waitingService.findWaiting(waiting.id());
        assertThat(arrived.status()).isEqualTo(WaitingStatus.ARRIVED);
        assertThat(arrived.arrivedAt()).isNotNull();
    }

    @Test
    void enterWaitingRequiresArrivedStatus() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        waitingService.notifyWaiting(waiting.id());

        waitingService.enterWaiting(waiting.id());
        assertThat(waitingService.findWaiting(waiting.id()).status()).isEqualTo(WaitingStatus.CALLED);

        waitingService.arriveWaiting(waiting.id());
        waitingService.enterWaiting(waiting.id());
        assertThat(waitingService.findWaiting(waiting.id()).status()).isEqualTo(WaitingStatus.ENTERED);
    }

    @Test
    void findWaitingThrowsWhenMissing() {
        assertThatThrownBy(() -> waitingService.findWaiting(999L))
                .isInstanceOf(WaitingNotFoundException.class)
                .hasMessage("Waiting not found: 999");
    }

    @Test
    void notifyWaitingSendsAlimtalkAndMarksNotified() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);

        boolean sent = waitingService.notifyWaiting(waiting.id());

        WaitingEntry notified = waitingService.findWaiting(waiting.id());
        assertThat(sent).isTrue();
        assertThat(waitingNotificationSender.sentWaitingIds()).containsExactly(waiting.id());
        assertThat(notified.status()).isEqualTo(WaitingStatus.CALLED);
        assertThat(notified.notifiedAt()).isNotNull();
    }

    @Test
    void notifyWaitingDoesNotSendDuplicateAlimtalk() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);

        boolean firstSent = waitingService.notifyWaiting(waiting.id());
        boolean secondSent = waitingService.notifyWaiting(waiting.id());

        assertThat(firstSent).isTrue();
        assertThat(secondSent).isFalse();
        assertThat(waitingNotificationSender.sentWaitingIds()).containsExactly(waiting.id());
    }

    @Test
    void notifyWaitingLeavesNotifiedAtEmptyWhenSendFails() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        waitingNotificationSender.fail();

        assertThatThrownBy(() -> waitingService.notifyWaiting(waiting.id()))
                .isInstanceOf(WaitingNotificationFailedException.class);

        WaitingEntry failed = waitingService.findWaiting(waiting.id());
        assertThat(failed.status()).isEqualTo(WaitingStatus.WAITING);
        assertThat(failed.notifiedAt()).isNull();
    }

    @Test
    void noShowWaitingMarksCalledWaitingAsNoShowed() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        waitingService.notifyWaiting(waiting.id());

        waitingService.noShowWaiting(waiting.id());
        assertThat(waitingService.findWaiting(waiting.id()).status()).isEqualTo(WaitingStatus.NO_SHOWED);
    }

    @Test
    void noShowWaitingMarksArrivedWaitingAsNoShowed() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        waitingService.notifyWaiting(waiting.id());
        waitingService.arriveWaiting(waiting.id());

        waitingService.noShowWaiting(waiting.id());
        assertThat(waitingService.findWaiting(waiting.id()).status()).isEqualTo(WaitingStatus.NO_SHOWED);
    }

    @Test
    void notifyShortageWaitingsCallsWaitingUpToCalledAndArrivedQueueShortage() {
        WaitingEntry first = waitingService.createWaiting("01012345678", true);
        WaitingEntry second = waitingService.createWaiting("01012345679", true);
        WaitingEntry third = waitingService.createWaiting("01012345670", true);
        WaitingEntry fourth = waitingService.createWaiting("01012345671", true);
        WaitingEntry fifth = waitingService.createWaiting("01012345672", true);
        WaitingEntry sixth = waitingService.createWaiting("01012345673", true);

        waitingService.notifyWaiting(first.id());
        waitingService.notifyWaiting(second.id());
        waitingService.arriveWaiting(second.id());

        int sentCount = waitingService.notifyShortageWaitings();

        assertThat(sentCount).isEqualTo(3);
        assertThat(waitingService.boothQueueCount(waitingService.activeWaitings())).isEqualTo(5);
        assertThat(waitingService.findWaiting(third.id()).status()).isEqualTo(WaitingStatus.CALLED);
        assertThat(waitingService.findWaiting(fourth.id()).status()).isEqualTo(WaitingStatus.CALLED);
        assertThat(waitingService.findWaiting(fifth.id()).status()).isEqualTo(WaitingStatus.CALLED);
        assertThat(waitingService.findWaiting(sixth.id()).status()).isEqualTo(WaitingStatus.WAITING);
    }

    private void makeCallOverdue(Long id) {
        jdbcClient.sql("""
                        update waiting_entry
                        set notified_at = :notifiedAt
                        where id = :id
                        """)
                .param("notifiedAt", LocalDateTime.now().minusMinutes(10))
                .param("id", id)
                .update();
    }

    @TestConfiguration
    static class NotificationTestConfiguration {

        @Bean
        @Primary
        TestWaitingNotificationSender testWaitingNotificationSender() {
            return new TestWaitingNotificationSender();
        }
    }

    static class TestWaitingNotificationSender implements WaitingNotificationSender {

        private final List<Long> sentWaitingIds = new ArrayList<>();
        private boolean failing;

        @Override
        public void sendCall(WaitingEntry waiting) {
            if (failing) {
                throw new WaitingNotificationFailedException(waiting.id(), new RuntimeException("boom"));
            }
            sentWaitingIds.add(waiting.id());
        }

        List<Long> sentWaitingIds() {
            return sentWaitingIds;
        }

        void fail() {
            failing = true;
        }

        void clear() {
            sentWaitingIds.clear();
            failing = false;
        }
    }
}
