package com.odatour.waiting;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OdatourWaitingSystemApplicationTests {

    @Value("${SOL_API_KEY}")
    private String solApiKey;

    @Value("${SOL_API_SECRET}")
    private String solApiSecret;

    @Test
    void contextLoads() {
    }

    @Test
    void sendSMS() {
        DefaultMessageService messageService = SolapiClient.INSTANCE.createInstance(solApiKey, solApiSecret);
        // Message 패키지가 중복될 경우 com.solapi.sdk.message.model.Message로 치환하여 주세요
        Message message = new Message();
        message.setFrom("01023294262");
        message.setTo("01022638630");
        message.setText("성준아 사랑해");

        try {
            // send 메소드로 ArrayList<Message> 객체를 넣어도 동작합니다!
            messageService.send(message);
        } catch (SolapiMessageNotReceivedException exception) {
            // 발송에 실패한 메시지 목록을 확인할 수 있습니다!
            System.out.println(exception.getFailedMessageList());
            System.out.println(exception.getMessage());
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
    }
}
