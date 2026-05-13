package com.odatour.waiting.notification;

import com.odatour.waiting.domain.WaitingEntry;

public interface WaitingNotificationSender {

    void sendCall(WaitingEntry waiting);

    void sendNoShow(WaitingEntry waiting);
}
