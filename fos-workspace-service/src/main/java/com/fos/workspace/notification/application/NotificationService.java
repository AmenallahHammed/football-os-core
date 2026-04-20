package com.fos.workspace.notification.application;

import com.fos.sdk.security.FosSecurityContext;
import com.fos.workspace.notification.api.NotificationResponse;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final WorkspaceNotificationRepository notificationRepository;
    private final FosSecurityContext securityContext;
    private final boolean securityEnabled;

    public NotificationService(WorkspaceNotificationRepository notificationRepository,
                               FosSecurityContext securityContext,
                               @Value("${fos.security.enabled:true}") boolean securityEnabled) {
        this.notificationRepository = notificationRepository;
        this.securityContext = securityContext;
        this.securityEnabled = securityEnabled;
    }

    /** Returns all notifications for the current actor (paginated, newest first) */
    public Page<NotificationResponse> getMyNotifications(boolean unreadOnly, Pageable pageable) {
        UUID actorId = currentActorId();
        if (unreadOnly) {
            return notificationRepository
                    .findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(actorId, pageable)
                    .map(NotificationResponse::from);
        }
        return notificationRepository
                .findByRecipientActorIdOrderByCreatedAtDesc(actorId, pageable)
                .map(NotificationResponse::from);
    }

    /** Returns the count of unread notifications (for the inbox badge) */
    public long countUnread() {
        UUID actorId = currentActorId();
        return notificationRepository.countByRecipientActorIdAndReadFalse(actorId);
    }

    /** Marks a specific notification as read */
    public void markRead(UUID notificationId) {
        UUID actorId = currentActorId();

        var notification = notificationRepository
                .findByResourceId(notificationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Notification not found: " + notificationId));

        // Security: actors can only mark their own notifications as read
        if (!notification.getRecipientActorId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot mark another actor's notification as read");
        }

        notification.markRead();
        notificationRepository.save(notification);
    }

    /** Marks all notifications for the current actor as read */
    public void markAllRead() {
        UUID actorId = currentActorId();
        notificationRepository
                .findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(
                        actorId, Pageable.unpaged())
                .forEach(n -> {
                    n.markRead();
                    notificationRepository.save(n);
                });
    }

    private UUID currentActorId() {
        return securityEnabled ? securityContext.getActorId() : FALLBACK_ACTOR_ID;
    }
}
