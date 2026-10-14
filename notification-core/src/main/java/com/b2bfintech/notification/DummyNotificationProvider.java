package com.b2bfintech.notification;

import com.b2bfintech.common.spi.NotificationEvent;
import com.b2bfintech.common.spi.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local/dev placeholder: logs every notification instead of delivering it. Swap for a real
 * SMS/email/push/webhook provider behind the same {@link NotificationProvider} interface later.
 */
@Component
public class DummyNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(DummyNotificationProvider.class);

    @Override
    public void send(NotificationEvent event) {
        log.info("[DUMMY NOTIFICATION] type={} phone={} tenant={} data={}",
                event.type(), event.recipientPhoneNumber(), event.tenantSchema(), event.data());
    }
}
