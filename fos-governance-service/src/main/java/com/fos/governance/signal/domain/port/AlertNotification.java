package com.fos.governance.signal.domain.port;

import java.util.UUID;

public record AlertNotification(
    UUID   recipientActorId,
    String title,
    String body,
    String topic
) {}
