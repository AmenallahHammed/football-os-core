package com.fos.workspace.event.application.reminder;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.workspace.event.domain.WorkspaceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends ALERT signals for required documents that are still missing.
 */
@Component
public class DocumentMissingReminderStrategy implements ReminderStrategy {

    private static final Logger log = LoggerFactory.getLogger(DocumentMissingReminderStrategy.class);

    private final FosKafkaProducer kafkaProducer;

    public DocumentMissingReminderStrategy(FosKafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public boolean shouldRemind(WorkspaceEvent event) {
        return event.hasMissingDocuments();
    }

    @Override
    public void sendReminder(WorkspaceEvent event) {
        event.getRequiredDocuments().stream()
                .filter(requiredDocument -> !requiredDocument.isSubmitted())
                .forEach(requiredDocument -> {
                    kafkaProducer.emit(SignalEnvelope.builder()
                            .type(SignalType.ALERT)
                            .topic("fos.workspace.event.document.missing")
                            .actorRef(CanonicalRef.of(CanonicalType.CLUB, requiredDocument.getAssignedToActorId()).toString())
                            .build());

                    log.info("Sent missing-document reminder: eventId={} actor={} requirement='{}'",
                            event.getResourceId(),
                            requiredDocument.getAssignedToActorId(),
                            requiredDocument.getDescription());
                });
    }
}
