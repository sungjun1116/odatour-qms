package com.odatour.waiting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.odatour.waiting.controller.view.WaitingStatusView;
import com.odatour.waiting.domain.WaitingEntry;
import com.odatour.waiting.notification.WaitingNotificationSender;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaitingPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void newWaitingShowsEstimatedWaitMinutesAndPhoneFormatterAttributes() throws Exception {
        createWaiting("01012345681");
        createWaiting("01012345682");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("waitings/new"))
                .andExpect(model().attribute("estimatedWaitMinutes", 6))
                .andExpect(content().string(containsString("data-phone-number-input")))
                .andExpect(content().string(containsString("pattern=\"010[0-9]{8}|010-[0-9]{4}-[0-9]{4}\"")));
    }

    @Test
    void createWaitingRedirectsToWaitingStatus() throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "010-1234-5678")
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/waitings/*"));
    }

    @Test
    void createWaitingShowsDuplicateActiveWaitingError() throws Exception {
        createWaiting("01012345678");

        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "010-1234-5678")
                        .param("consentAgreed", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("waitings/new"))
                .andExpect(model().attribute("errorMessage", "이미 웨이팅 중인 휴대폰 번호입니다."))
                .andExpect(model().attributeExists("activeWaitingId"))
                .andExpect(model().attribute("phoneNumber", "010-1234-5678"));
    }

    @Test
    void createWaitingShowsInvalidPhoneNumberError() throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "010-123-5678")
                        .param("consentAgreed", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("waitings/new"))
                .andExpect(model().attribute("errorMessage", "휴대폰 번호 형식이 올바르지 않습니다."));
    }

    @Test
    void waitingStatusShowsRemainingCountAndEstimatedWaitMinutes() throws Exception {
        createWaiting("01012345691");
        createWaiting("01012345692");
        createWaiting("01012345693");
        Long id = findIdByPhoneNumber("01012345693");

        mockMvc.perform(get("/waitings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("waitings/status"))
                .andExpect(result -> {
                    WaitingStatusView waiting = (WaitingStatusView) result.getModelAndView()
                            .getModel()
                            .get("waiting");
                    assertThat(waiting.remainingCount()).isEqualTo(2);
                    assertThat(waiting.estimatedWaitMinutes()).isEqualTo(6);
                    assertThat(waiting.maskedPhoneNumber()).isEqualTo("010-****-5693");
                });
    }

    @Test
    void cancelWaitingRedirectsBackToStatus() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");

        mockMvc.perform(post("/waitings/{id}/cancel", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/waitings/" + id));
    }

    @Test
    void adminWaitingsShowsActiveSummary() throws Exception {
        createWaiting("01012345678");
        createWaiting("01012345679");

        mockMvc.perform(get("/admin/waitings"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/waitings"))
                .andExpect(model().attributeExists(
                        "summary",
                        "waitings",
                        "sectionHeadings",
                        "page",
                        "enteredCount",
                        "boothQueueCapacity",
                        "boothQueueCount",
                        "callShortageCount"
                ))
                .andExpect(content().string(containsString("호출하기")))
                .andExpect(content().string(containsString("취소처리")))
                .andExpect(content().string(containsString("이 고객을 호출 처리할까요?")))
                .andExpect(content().string(containsString("부족 인원을 호출 처리할까요?")));
    }

    @Test
    void notifyWaitingRedirectsToAdminWaitingsAndMarksCalled() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");

        mockMvc.perform(post("/admin/waitings/{id}/notify", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/waitings"));

        assertThat(waitingNotificationSender.sentWaitingIds()).containsExactly(id);
        assertThat(findStatusById(id)).isEqualTo("CALLED");
    }

    @Test
    void adminEnteredWaitingsShowsEnteredRows() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");
        mockMvc.perform(post("/admin/waitings/{id}/notify", id))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/waitings/{id}/arrive", id))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/waitings/{id}/enter", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/waitings"));

        mockMvc.perform(get("/admin/waitings/entered"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/entered-waitings"))
                .andExpect(model().attributeExists("enteredWaitings", "page", "activeCount"));
    }

    @Test
    void arriveWaitingRedirectsToAdminWaitingsAndMarksArrived() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");
        mockMvc.perform(post("/admin/waitings/{id}/notify", id))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/waitings/{id}/arrive", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/waitings"));

        assertThat(findStatusById(id)).isEqualTo("ARRIVED");
    }

    @Test
    void noShowWaitingRedirectsToAdminWaitings() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");
        mockMvc.perform(post("/admin/waitings/{id}/notify", id))
                .andExpect(status().is3xxRedirection());
        makeCallOverdue(id);

        mockMvc.perform(post("/admin/waitings/{id}/no-show", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/waitings"));

        assertThat(findStatusById(id)).isEqualTo("NO_SHOWED");
    }

    @Test
    void adminCancelWaitingRedirectsToAdminWaitingsAndMarksCanceled() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");

        mockMvc.perform(post("/admin/waitings/{id}/cancel", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/waitings"));

        assertThat(findStatusById(id)).isEqualTo("CANCELED");
    }

    @Test
    void adminWaitingsShowsCalledActionsAndOverdueLabelAfterTenMinutes() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");
        mockMvc.perform(post("/admin/waitings/{id}/notify", id))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/waitings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CALLED 고객")))
                .andExpect(content().string(containsString("/js/admin-waitings.js")))
                .andExpect(content().string(containsString("data-called-at=")))
                .andExpect(content().string(containsString("호출 후 경과")))
                .andExpect(content().string(containsString(">현장도착 확인</button>")))
                .andExpect(content().string(containsString("이 고객을 현장도착 처리할까요?")))
                .andExpect(content().string(containsString(">노쇼</button>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">입장완료</button>"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">취소처리</button>"))))
                .andExpect(content().string(containsString("overdue-label  hidden")));

        makeCallOverdue(id);

        mockMvc.perform(get("/admin/waitings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("호출 후 10분 경과")))
                .andExpect(content().string(containsString("이 고객을 노쇼 처리할까요?")))
                .andExpect(content().string(containsString(">노쇼</button>")));
    }

    @Test
    void adminWaitingsShowsEnterOnlyForArrivedCustomer() throws Exception {
        createWaiting("01012345678");
        Long id = findIdByPhoneNumber("01012345678");
        mockMvc.perform(post("/admin/waitings/{id}/notify", id))
                .andExpect(status().is3xxRedirection());
        makeCallOverdue(id);
        mockMvc.perform(post("/admin/waitings/{id}/arrive", id))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/waitings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ARRIVED 고객")))
                .andExpect(content().string(containsString(">입장완료</button>")))
                .andExpect(content().string(containsString("이 고객을 입장 완료 처리할까요?")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-called-at="))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("호출 후 경과"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("호출 후 10분 경과"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">현장도착 확인</button>"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">노쇼</button>"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">취소처리</button>"))));
    }

    @Test
    void notifyShortageCallsWaitingCustomers() throws Exception {
        createWaiting("01012345678");
        createWaiting("01012345679");

        mockMvc.perform(post("/admin/waitings/notify-shortage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/waitings"));

        assertThat(waitingNotificationSender.sentWaitingIds()).containsExactly(
                findIdByPhoneNumber("01012345678"),
                findIdByPhoneNumber("01012345679")
        );
    }

    private void createWaiting(String phoneNumber) throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", phoneNumber)
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection());
    }

    private Long findIdByPhoneNumber(String phoneNumber) {
        return jdbcClient.sql("""
                        select id
                        from waiting_entry
                        where phone_number = :phoneNumber
                        """)
                .param("phoneNumber", phoneNumber)
                .query(Long.class)
                .single();
    }

    private String findStatusById(Long id) {
        return jdbcClient.sql("""
                        select status
                        from waiting_entry
                        where id = :id
                        """)
                .param("id", id)
                .query(String.class)
                .single();
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

        @Override
        public void sendCall(WaitingEntry waiting) {
            sentWaitingIds.add(waiting.id());
        }

        List<Long> sentWaitingIds() {
            return sentWaitingIds;
        }

        void clear() {
            sentWaitingIds.clear();
        }
    }
}
