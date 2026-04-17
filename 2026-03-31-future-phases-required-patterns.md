# Required Patterns for Future Phases — Planning Notes

> These items were identified during Phase 0 planning review. They MUST be incorporated when planning the relevant phase. Do not defer them again.

---

## Issue #4 (🔴) — Saga Pattern for Cross-Domain Transactions

**Affects:** Phase 2 — fos-ingest-service planning

**Problem:** The ingest → canonical → warehouse flow involves writes across three services. If canonical write succeeds but the warehouse consumer fails permanently, data is inconsistent with no recovery path.

**Required:** Implement the Saga (choreography-based) pattern in `fos-ingest-service`.

**Saga steps for an import job:**
```
1. Parse raw data              → compensate: no-op (nothing written yet)
2. Deduplicate against canonical → compensate: no-op
3. Write to canonical service  → compensate: mark canonical entities as ARCHIVED (soft delete)
4. Emit FACT signals to Kafka  → warehouse consumer projects them
5. Poll warehouse for confirmation (or wait for warehouse.projection.complete FACT)
6. Mark import job as COMPLETE → compensate: mark as FAILED
```

**Implementation notes for Phase 2 Sprint 2.1:**
- `ImportJob` entity must track `SagaState`: STARTED → PARSED → CANONICAL_WRITTEN → WAREHOUSE_PROJECTED → COMPLETE / FAILED
- Each step transition emits a FACT signal
- A `SagaCoordinator` bean in `fos-ingest-service` orchestrates the steps and handles compensation
- Compensation signals go to `fos.ingest.saga.compensate` topic
- Max retry: 3 attempts per step. After 3 failures → FAILED state + ALERT signal to operators
- `AbstractImportJob` (Template Method from DESIGN-PATTERNS.md §14) must include saga state tracking in its `execute()` flow

**When planning Phase 2 Sprint 2.1:** Add a dedicated task "Implement Saga Coordinator for import jobs" before the Wyscout CSV connector task.

---

## Issue #7 (🟡) — Football Domain Validation Rules

**Affects:** Phase 2 — fos-ingest-service (all connectors)

**Problem:** Wyscout/GPExe imports lack football-specific validation. Invalid data passes through silently and corrupts canonical entities.

**Required validation rules** (add to the import validation chain — DESIGN-PATTERNS.md §15):

```java
// These validators extend RecordValidator (Chain of Responsibility — DESIGN-PATTERNS.md §15)

class PlayerPositionValidator extends RecordValidator {
    private static final Set<String> VALID_POSITIONS = Set.of(
        "GK", "CB", "LB", "RB", "LWB", "RWB",
        "CDM", "CM", "CAM", "LM", "RM",
        "LW", "RW", "CF", "ST"
    );
    // Rejects records where position is not in VALID_POSITIONS
}

class MatchScoreValidator extends RecordValidator {
    // Goals must be 0–99 (not negative, not > 99)
    // Both home and away score must be present if either is present
}

class TrainingSessionTypeValidator extends RecordValidator {
    private static final Set<String> VALID_TYPES = Set.of(
        "physical", "tactical", "technical", "goalkeeper", "recovery", "match_prep"
    );
}

class SeasonDateRangeValidator extends RecordValidator {
    // Season start must be before season end
    // Season dates cannot overlap with existing seasons for the same team
}
```

**Validation chain for player imports:**
```
RequiredFieldsValidator
  → PlayerPositionValidator
  → DataTypeValidator (date formats, numeric ranges)
  → BatchDeduplicationValidator
```

**When planning Phase 2 Sprint 2.1:** Add task "Implement football domain validation chain" before the connector task.

---

## Issue #8 (🟡) — MongoDB Schema Migration Strategy

**Affects:** Phase 1 — fos-workspace-service and all subsequent domain services

**Problem:** INFRASTRUCTURE.md mentions Flyway for PostgreSQL but nothing for MongoDB. When adding fields to workspace documents in a later sprint, there is no migration path for existing data.

**Solution:** Use **Mongock** for MongoDB migrations.

**Add to every domain service that uses MongoDB:**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.mongock</groupId>
  <artifactId>mongock-springboot-v3</artifactId>
  <version>5.4.4</version>
</dependency>
<dependency>
  <groupId>io.mongock</groupId>
  <artifactId>mongodb-springdata-v4-driver</artifactId>
  <version>5.4.4</version>
</dependency>
```

```yaml
# application.yml
mongock:
  migration-scan-package: com.fos.{service}.infrastructure.migration
  enabled: true
```

**Migration class pattern:**

```java
// src/main/java/com/fos/workspace/infrastructure/migration/Migration001AddSpaceType.java
@ChangeUnit(id = "migration-001-add-space-type", order = "001", author = "fos-team")
public class Migration001AddSpaceType {

    @Execution
    public void addSpaceTypeField(MongoTemplate mongoTemplate) {
        var update = new Update().set("spaceType", "GENERAL");
        mongoTemplate.updateMulti(new Query(), update, "spaces");
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        var update = new Update().unset("spaceType");
        mongoTemplate.updateMulti(new Query(), update, "spaces");
    }
}
```

**Rules:**
- Every Mongock migration must have a `@RollbackExecution` method
- Migration IDs are immutable once merged to main — never change an executed migration's ID
- New field additions default to a safe value (never null for required fields)

**When planning Phase 1 Sprint 1.1:** Add task "Set up Mongock for fos-workspace-service" as the first task in the workspace service setup.

---

## Summary Table

| Issue | Severity | Phase | Sprint | Action |
|-------|----------|-------|--------|--------|
| Saga pattern | 🔴 | Phase 2 | Sprint 2.1 | Add `SagaCoordinator` + `SagaState` to import job |
| Football validation | 🟡 | Phase 2 | Sprint 2.1 | Add validation chain with position/score/type/date rules |
| MongoDB migration | 🟡 | Phase 1 | Sprint 1.1 | Add Mongock to all domain services using MongoDB |
