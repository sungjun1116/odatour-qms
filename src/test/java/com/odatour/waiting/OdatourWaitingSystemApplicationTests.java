package com.odatour.waiting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.odatour.waiting.web.WaitingPageController.WaitingStatusView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OdatourWaitingSystemApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("delete from waiting_entry").update();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void createWaitingPersistsPhoneNumber() throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "010-1234-5678")
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/waitings/*"));

        Integer count = jdbcClient.sql("""
                        select count(*)
                        from waiting_entry
                        where phone_number = '01012345678'
                          and status = 'WAITING'
                        """)
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicateActivePhoneNumberIsRejected() throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "01012345679")
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "010-1234-5679")
                        .param("consentAgreed", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("waitings/new"))
                .andExpect(model().attribute("errorMessage", "이미 웨이팅 중인 휴대폰 번호입니다."));

        Integer count = jdbcClient.sql("""
                        select count(*)
                        from waiting_entry
                        where phone_number = '01012345679'
                        """)
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidPhoneNumberIsRejected() throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "010-123-5678")
                        .param("consentAgreed", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("waitings/new"))
                .andExpect(model().attribute("errorMessage", "휴대폰 번호 형식이 올바르지 않습니다."));

        Integer count = jdbcClient.sql("select count(*) from waiting_entry")
                .query(Integer.class)
                .single();

        assertThat(count).isZero();
    }

    @Test
    void canceledPhoneNumberCanRegisterAgain() throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "01012345670")
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection());

        Long id = jdbcClient.sql("""
                        select id
                        from waiting_entry
                        where phone_number = '01012345670'
                        """)
                .query(Long.class)
                .single();

        mockMvc.perform(post("/waitings/{id}/cancel", id))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", "01012345670")
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection());

        Integer count = jdbcClient.sql("""
                        select count(*)
                        from waiting_entry
                        where phone_number = '01012345670'
                        """)
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void newWaitingShowsEstimatedWaitMinutesByActiveTeamCount() throws Exception {
        createWaiting("01012345681");
        createWaiting("01012345682");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estimatedWaitMinutes", 6));
    }

    @Test
    void waitingStatusShowsEstimatedWaitMinutesByRemainingTeamCount() throws Exception {
        createWaiting("01012345691");
        createWaiting("01012345692");
        createWaiting("01012345693");

        Long id = jdbcClient.sql("""
                        select id
                        from waiting_entry
                        where phone_number = '01012345693'
                        """)
                .query(Long.class)
                .single();

        mockMvc.perform(get("/waitings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    WaitingStatusView waiting = (WaitingStatusView) result.getModelAndView()
                            .getModel()
                            .get("waiting");
                    assertThat(waiting.estimatedWaitMinutes()).isEqualTo(6);
                });
    }

    private void createWaiting(String phoneNumber) throws Exception {
        mockMvc.perform(post("/waitings")
                        .param("phoneNumber", phoneNumber)
                        .param("consentAgreed", "true"))
                .andExpect(status().is3xxRedirection());
    }
}
