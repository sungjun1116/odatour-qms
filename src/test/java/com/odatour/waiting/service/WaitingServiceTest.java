package com.odatour.waiting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.domain.WaitingStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WaitingServiceTest {

    @Autowired
    private WaitingService waitingService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("delete from waiting_entry").update();
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
    void activeWaitingsReturnsWaitingAndCalledOnly() {
        WaitingEntry waiting = waitingService.createWaiting("01012345678", true);
        WaitingEntry entered = waitingService.createWaiting("01012345679", true);
        WaitingEntry noShow = waitingService.createWaiting("01012345670", true);

        waitingService.enterWaiting(entered.id());
        waitingService.noShowWaiting(noShow.id());

        assertThat(waitingService.activeWaitings())
                .extracting(WaitingEntry::id)
                .containsExactly(waiting.id());
    }

    @Test
    void remainingCountReturnsNumberOfActiveWaitingsBeforeTarget() {
        WaitingEntry first = waitingService.createWaiting("01012345678", true);
        WaitingEntry second = waitingService.createWaiting("01012345679", true);
        WaitingEntry third = waitingService.createWaiting("01012345670", true);
        waitingService.enterWaiting(first.id());

        List<WaitingEntry> activeWaitings = waitingService.activeWaitings();

        assertThat(waitingService.remainingCount(second, activeWaitings)).isZero();
        assertThat(waitingService.remainingCount(third, activeWaitings)).isEqualTo(1);
        assertThat(waitingService.remainingCount(waitingService.findWaiting(first.id()), activeWaitings)).isZero();
    }

    @Test
    void findWaitingThrowsWhenMissing() {
        assertThatThrownBy(() -> waitingService.findWaiting(999L))
                .isInstanceOf(WaitingNotFoundException.class)
                .hasMessage("Waiting not found: 999");
    }
}
