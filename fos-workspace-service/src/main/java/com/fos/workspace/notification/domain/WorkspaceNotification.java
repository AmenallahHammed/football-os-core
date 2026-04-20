package com.fos.workspace.notification.domain;

import com.fos.sdk.core.BaseDocument;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/**
 * A single notification in an actor's inbox.
 *
 * Extends BaseDocument (sdk-core) -> id, resourceId, state, version,
 * createdAt, updatedAt.
 *
 * Notifications are NEVER deleted - they are marked as read.
 * This gives us a full audit trail of what was communicated to each actor.
 *
 * recipientActorId: who should see this notification in their inbox
 * triggeredByActorId: who (or what system process) triggered this notification
 */
@Document(collection = "workspace_notifications")
public class WorkspaceNotification extends BaseDocument {

    private UUID recipientActorId;
    private UUID triggeredByActorId;    // null for system-generated notifications
    private NotificationType type;
    private String title;
    private String body;
    private boolean read;

    /** Optional reference to the entity this notification is about */
    private UUID relatedDocumentId;
    private UUID relatedEventId;

    protected WorkspaceNotification() {}

    public static WorkspaceNotification create(UUID recipientActorId,
                                                UUID triggeredByActorId,
                                                NotificationType type,
                                                String title,
                                                String body,
                                                UUID relatedDocumentId,
                                                UUID relatedEventId) {
        WorkspaceNotification n = new WorkspaceNotification();
        n.initId();
        n.recipientActorId = recipientActorId;
        n.triggeredByActorId = triggeredByActorId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.read = false;
        n.relatedDocumentId = relatedDocumentId;
        n.relatedEventId = relatedEventId;
        n.activate(); // notifications are active immediately
        return n;
    }

    public void markRead() { this.read = true; }

    public UUID getRecipientActorId()    { return recipientActorId; }
    public UUID getTriggeredByActorId()  { return triggeredByActorId; }
    public NotificationType getType()    { return type; }
    public String getTitle()             { return title; }
    public String getBody()              { return body; }
    public boolean isRead()              { return read; }
    public UUID getRelatedDocumentId()   { return relatedDocumentId; }
    public UUID getRelatedEventId()      { return relatedEventId; }
}
