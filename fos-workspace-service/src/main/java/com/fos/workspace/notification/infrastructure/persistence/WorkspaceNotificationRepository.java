package com.fos.workspace.notification.infrastructure.persistence;

import com.fos.workspace.notification.domain.WorkspaceNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceNotificationRepository
        extends MongoRepository<WorkspaceNotification, String> {

    /** Returns all notifications for a specific actor (inbox), newest first */
    Page<WorkspaceNotification> findByRecipientActorIdOrderByCreatedAtDesc(
            UUID recipientActorId, Pageable pageable);

    /** Returns only UNREAD notifications for an actor */
    Page<WorkspaceNotification> findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(
            UUID recipientActorId, Pageable pageable);

    /** Used for marking a notification as read */
    Optional<WorkspaceNotification> findByResourceId(UUID resourceId);

    /** Count unread notifications for badge display */
    long countByRecipientActorIdAndReadFalse(UUID recipientActorId);
}
