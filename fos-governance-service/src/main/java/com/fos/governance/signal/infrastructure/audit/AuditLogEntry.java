package com.fos.governance.signal.infrastructure.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record in fos_audit.audit_log.
 */
@Entity
@Table(schema = "fos_audit", name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false, unique = true)
    private UUID signalId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action")
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "topic")
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected AuditLogEntry() {}

    public AuditLogEntry(UUID signalId, UUID actorId, String action,
                          String resourceType, UUID resourceId, String topic, JsonNode payload) {
        this.signalId = signalId;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.topic = topic;
        this.payload = payload;
        this.recordedAt = Instant.now();
    }

    public UUID getSignalId() { return signalId; }
    public Long getId()       { return id; }
}
