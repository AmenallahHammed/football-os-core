package com.fos.governance.signal.infrastructure.audit;

import com.fos.sdk.events.AbstractFosConsumer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for auditing all signals.
 */
@Component
public class AuditLogConsumer extends AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditLogConsumer.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogConsumer(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        super(objectMapper);
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @KafkaListener(topics = KafkaTopics.AUDIT_ALL, groupId = "fos-governance-audit")
    public void onMessage(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        super.onMessage(record);
    }

    @Override
    protected void handle(SignalEnvelope envelope) {
        if (auditLogRepository.existsBySignalId(envelope.signalId())) {
            log.debug("Audit signal already processed (idempotent skip): signalId={}",
                    envelope.signalId());
            return;
        }

        try {
            com.fos.sdk.canonical.CanonicalRef actorRef = envelope.actorRef() != null 
                    ? com.fos.sdk.canonical.CanonicalRef.parse(envelope.actorRef()) : null;

            UUID actorId = actorRef != null ? actorRef.id() : null;
            String resourceType = actorRef != null
                    ? actorRef.type().name() : null;

            AuditLogEntry entry = new AuditLogEntry(
                    envelope.signalId(),
                    actorId,
                    envelope.topic(),
                    resourceType,
                    actorId, // fallback for resource_id
                    envelope.topic(),
                    envelope.payload()
            );

            auditLogRepository.save(entry);
            log.debug("Audit log written: signalId={} topic={}", envelope.signalId(), envelope.topic());

        } catch (DataIntegrityViolationException e) {
            log.debug("Audit duplicate detected (concurrent write): signalId={}", envelope.signalId());
        } catch (Exception e) {
            log.error("Failed to write audit log: signalId={}", envelope.signalId(), e);
            throw new RuntimeException(e);
        }
    }
}
