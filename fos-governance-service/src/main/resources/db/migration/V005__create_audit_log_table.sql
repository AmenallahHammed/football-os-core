-- V005__create_audit_log_table.sql
-- Append-only audit log. Written by the AuditLogConsumer from Kafka topic fos.audit.all.

CREATE TABLE fos_audit.audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    signal_id      UUID         NOT NULL,
    actor_id       UUID,
    action         VARCHAR(255) NOT NULL,
    resource_type  VARCHAR(100),
    resource_id    UUID,
    topic          VARCHAR(255),
    payload        JSONB,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Idempotency: prevent duplicate processing of the same signal
CREATE UNIQUE INDEX uidx_audit_log_signal_id ON fos_audit.audit_log(signal_id);

CREATE INDEX idx_audit_log_actor_id   ON fos_audit.audit_log(actor_id);
CREATE INDEX idx_audit_log_resource   ON fos_audit.audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_recorded   ON fos_audit.audit_log(recorded_at DESC);
