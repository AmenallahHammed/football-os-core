package com.fos.governance.signal.application.pipeline;

import com.fos.governance.signal.domain.port.AlertNotification;
import com.fos.governance.signal.domain.port.NotificationPort;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;

import java.util.UUID;

public class NotificationFanOutHandler extends SignalHandler {

    private final NotificationPort notificationPort;

    public NotificationFanOutHandler(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        if (signal.type() == SignalType.ALERT) {
            com.fos.sdk.canonical.CanonicalRef actorRef = signal.actorRef() != null 
                    ? com.fos.sdk.canonical.CanonicalRef.parse(signal.actorRef()) : null;
            UUID recipientId = actorRef != null ? actorRef.id() : null;
            if (recipientId != null) {
                notificationPort.sendAlert(new AlertNotification(
                        recipientId,
                        "Alert: " + signal.topic(),
                        signal.payload() != null ? signal.payload().toString() : "",
                        signal.topic()
                ));
            }
        }
        return signal;
    }
}
