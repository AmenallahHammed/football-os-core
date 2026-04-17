package com.fos.governance.signal.infrastructure.notification;

import com.fos.governance.signal.domain.port.AlertNotification;
import com.fos.governance.signal.domain.port.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Null Object adapter for NotificationPort.
 */
public class NoopNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NoopNotificationAdapter.class);

    @Override
    public void sendAlert(AlertNotification notification) {
        log.info("[NOOP] Alert suppressed: actorId={} title='{}'",
                notification.recipientActorId(), notification.title());
    }
}
