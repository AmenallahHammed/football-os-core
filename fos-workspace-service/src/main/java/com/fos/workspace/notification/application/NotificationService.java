package com.fos.workspace.notification.application;

import com.fos.sdk.security.FosSecurityContext;
import com.fos.workspace.notification.api.NotificationResponse;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    private final WorkspaceNotificationRepository notificationRepository;

    public NotificationService(WorkspaceNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Returns all notifications for the current actor (paginated, newest first) */
    public Page<NotificationResponse> getMyNotifications(boolean unreadOnly, Pageable pageable) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
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
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        return notificationRepository.countByRecipientActorIdAndReadFalse(actorId);
    }

    /** Marks a specific notification as read */
    public void markRead(UUID notificationId) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());

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
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        notificationRepository
                .findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(
                        actorId, Pageable.unpaged())
                .forEach(n -> {
                    n.markRead();
                    notificationRepository.save(n);
                });
    }
}
