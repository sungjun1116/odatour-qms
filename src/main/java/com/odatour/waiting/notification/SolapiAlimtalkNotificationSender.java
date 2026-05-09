package com.odatour.waiting.notification;

import com.odatour.waiting.domain.WaitingEntry;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.model.kakao.KakaoOption;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SolapiAlimtalkNotificationSender implements WaitingNotificationSender {

    private final String apiKey;
    private final String apiSecretKey;
    private final String from;
    private final String pfId;
    private final String templateId;
    private final boolean disableSms;

    public SolapiAlimtalkNotificationSender(
            @Value("${solapi.api-key:}") String apiKey,
            @Value("${solapi.api-secret-key:}") String apiSecretKey,
            @Value("${solapi.from:}") String from,
            @Value("${solapi.kakao.pf-id:}") String pfId,
            @Value("${solapi.kakao.template-id:}") String templateId,
            @Value("${solapi.kakao.disable-sms:true}") boolean disableSms
    ) {
        this.apiKey = apiKey;
        this.apiSecretKey = apiSecretKey;
        this.from = from;
        this.pfId = pfId;
        this.templateId = templateId;
        this.disableSms = disableSms;
    }

    @Override
    public void sendCall(WaitingEntry waiting) {
        validateRequiredProperties();

        KakaoOption kakaoOption = new KakaoOption();
        kakaoOption.setPfId(pfId);
        kakaoOption.setTemplateId(templateId);
        kakaoOption.setDisableSms(disableSms);

        Message message = new Message();
        message.setFrom(from);
        message.setTo(waiting.phoneNumber());
        message.setKakaoOptions(kakaoOption);

        try {
            messageService().send(message);
        } catch (SolapiMessageNotReceivedException exception) {
            throw new WaitingNotificationFailedException(waiting.id(), exception);
        } catch (Exception exception) {
            throw new WaitingNotificationFailedException(waiting.id(), exception);
        }
    }

    private DefaultMessageService messageService() {
        return SolapiClient.INSTANCE.createInstance(apiKey, apiSecretKey);
    }

    private void validateRequiredProperties() {
        if (isBlank(apiKey) || isBlank(apiSecretKey) || isBlank(from) || isBlank(pfId) || isBlank(templateId)) {
            throw new IllegalStateException("SOLAPI 카카오 알림톡 설정이 누락되었습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
