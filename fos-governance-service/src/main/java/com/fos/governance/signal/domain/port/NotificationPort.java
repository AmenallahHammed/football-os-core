package com.fos.governance.signal.domain.port;

/**
 * Port for notification delivery.
 */
public interface NotificationPort {
    void sendAlert(AlertNotification notification);
}
