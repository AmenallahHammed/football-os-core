package com.fos.workspace.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.AbstractFosConsumer;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.workspace.notification.domain.NotificationType;
import com.fos.workspace.notification.domain.WorkspaceNotification;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for workspace-relevant signals.
 *
 * Extends AbstractFosConsumer - Template Method pattern:
 *   The base class handles: deserialization, correlation ID extraction,
 *   MDC logging, error handling, and DLQ routing.
 *   This class only implements handle() - the domain logic.
 *
 * Listens to two topics:
 *   - fos.workspace.event.document.missing: sent by EventReminderScheduler
 *   - fos.workspace.document.uploaded: sent by DocumentService
 *
 * On receiving a signal, writes a WorkspaceNotification to MongoDB
 * for the target actor to see in their inbox.
 */
@Component
public class WorkspaceKafkaConsumer extends AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceKafkaConsumer.class);

    private final WorkspaceNotificationRepository notificationRepository;

    public WorkspaceKafkaConsumer(ObjectMapper objectMapper,
                                   WorkspaceNotificationRepository notificationRepository) {
        super(objectMapper);
        this.notificationRepository = notificationRepository;
    }

    @KafkaListener(
        topics = {
            "fos.workspace.event.document.missing",
            "fos.workspace.document.uploaded",
            "fos.workspace.event.created"
        },
        groupId = "fos-workspace-notifications"
    )
    public void consume(ConsumerRecord<String, String> record) {
        onMessage(record);
    }

    @Override
    protected void handle(SignalEnvelope envelope) {
        log.debug("Processing workspace signal: topic={} signalId={}",
                envelope.topic(), envelope.signalId());

        switch (envelope.topic()) {

            case "fos.workspace.event.document.missing" -> {
                // actorRef is the actor who must submit the missing document
                UUID recipientId = extractActorId(envelope.actorRef());
                if (recipientId == null) return;

                WorkspaceNotification notification = WorkspaceNotification.create(
                        recipientId,
                        null, // system-generated
                        NotificationType.DOCUMENT_MISSING,
                        "Action Required: Missing Document",
                        "You have a required document that must be submitted before an upcoming event.",
                        null, null);

                notificationRepository.save(notification);
                log.info("Saved DOCUMENT_MISSING notification for actor={}", recipientId);
            }

            case "fos.workspace.document.uploaded" -> {
                // For document uploads, we could notify linked players or team members.
                // For Phase 1, we notify the uploader with a confirmation.
                UUID uploaderId = extractActorId(envelope.actorRef());
                if (uploaderId == null) return;

                WorkspaceNotification notification = WorkspaceNotification.create(
                        uploaderId,
                        uploaderId,
                        NotificationType.DOCUMENT_UPLOADED,
                        "Document Uploaded Successfully",
                        "Your document has been uploaded and is now available in the workspace.",
                        null, null);

                notificationRepository.save(notification);
                log.info("Saved DOCUMENT_UPLOADED notification for actor={}", uploaderId);
            }

            case "fos.workspace.event.created" -> {
                // Notify the creator - confirmation that their event was saved.
                UUID creatorId = extractActorId(envelope.actorRef());
                if (creatorId == null) return;

                WorkspaceNotification notification = WorkspaceNotification.create(
                        creatorId,
                        creatorId,
                        NotificationType.EVENT_REMINDER,
                        "Event Created",
                        "Your calendar event has been created.",
                        null, null);

                notificationRepository.save(notification);
            }

            default -> log.debug("Unhandled topic in WorkspaceKafkaConsumer: {}", envelope.topic());
        }
    }

    /**
     * Extracts a UUID from the actorRef string.
     * actorRef is a CanonicalRef.toString() in the format "CanonicalRef[type=CLUB, id=uuid]".
     * This is a simplified parser - in production use CanonicalRef deserialization.
     */
    private UUID extractActorId(String actorRefString) {
        if (actorRefString == null) return null;
        try {
            String ref = actorRefString.trim();

            // Simple extraction: find the UUID pattern in the string
            // CanonicalRef.toString() -> "CanonicalRef[type=CLUB, id=<UUID>]"
            // We extract the UUID after "id="
            int idIndex = ref.indexOf("id=");
            if (idIndex == -1) {
                // CanonicalRef compact format -> "CLUB:<UUID>"
                int colonIndex = ref.indexOf(':');
                if (colonIndex != -1 && colonIndex < ref.length() - 1) {
                    return UUID.fromString(ref.substring(colonIndex + 1).trim());
                }

                // Try parsing directly as UUID (if the caller passed UUID.toString())
                return UUID.fromString(ref);
            }
            String uuidPart = ref.substring(idIndex + 3).replace("]", "").trim();
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            log.warn("Could not extract actorId from actorRef: {}", actorRefString);
            return null;
        }
    }
}
