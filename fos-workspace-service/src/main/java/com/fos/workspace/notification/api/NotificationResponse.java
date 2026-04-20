package com.fos.workspace.notification.api;

import com.fos.workspace.notification.domain.NotificationType;
import com.fos.workspace.notification.domain.WorkspaceNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID notificationId,
    NotificationType type,
    String title,
    String body,
    boolean read,
    UUID relatedDocumentId,
    UUID relatedEventId,
    Instant createdAt
) {
    public static NotificationResponse from(WorkspaceNotification n) {
        return new NotificationResponse(
                n.getResourceId(), n.getType(), n.getTitle(), n.getBody(),
                n.isRead(), n.getRelatedDocumentId(), n.getRelatedEventId(),
                n.getCreatedAt());
    }
}
