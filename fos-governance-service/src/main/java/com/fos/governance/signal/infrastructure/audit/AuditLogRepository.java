package com.fos.governance.signal.infrastructure.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
    boolean existsBySignalId(UUID signalId);
}
