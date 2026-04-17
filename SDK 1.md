# Football OS — fos-sdk

The SDK is a Maven library published from the `football-os-core` monorepo. It is the **only** dependency that domain services import from the core platform. It contains no business logic — only contracts, clients, base classes, and utilities.

**Rule:** If a domain service needs something from governance core, it does it through the SDK. Never a direct HTTP call to a governance service, never a shared database query.

---

## Modules

### sdk-core

Base types shared across all services and domains.

```
BaseEntity          — JPA base with resource_id, created_at, updated_at, version
BaseDocument        — MongoDB base equivalent of BaseEntity
PageResponse<T>     — standard paginated response envelope
ErrorResponse       — standard error response (code, message, details)
ResourceState       — enum: DRAFT, ACTIVE, ARCHIVED
FosRoles            — constants for all actor role names
```

Every domain service depends on this module.

---

### sdk-events

Kafka integration for producing and consuming signals.

```
SignalEnvelope       — the typed signal wrapper (see ARCHITECTURE.md)
SignalType           — enum: INTENT, FACT, ALERT, AUDIT
FosKafkaProducer     — configured producer, injects correlation_id automatically
AbstractFosConsumer  — base Kafka consumer with error handling and retry
KafkaTopics          — constants for all topic names
```

Domain services extend `AbstractFosConsumer` to react to events. They use `FosKafkaProducer` to emit facts.

---

### sdk-security

JWT extraction and audit annotation.

```
FosSecurityContext   — extracts actor_id, roles, club_id from JWT for the current request
FosJwtConverter      — Spring Security converter for Keycloak JWTs
@Audited             — method annotation that automatically emits an AUDIT signal
AuditAspect          — Spring AOP aspect that processes @Audited
```

Every domain service includes this module. `FosSecurityContext` is the only way to get the current actor — never parse the JWT manually.

---

### sdk-policy

Policy check client for ABAC decisions.

```
PolicyClient         — sends PolicyRequest to fos-policy-service, returns PolicyResult
PolicyRequest        — actor_ref, action, resource_ref, context attributes
PolicyResult         — decision: ALLOW | DENY | ESCALATE, reason string
@PolicyGuard         — method annotation for declarative policy checks
PolicyGuardAspect    — Spring AOP aspect that processes @PolicyGuard
```

Usage in a domain service:
```java
@PolicyGuard(action = "workspace.file.read")
public FileDTO getFile(UUID fileId) { ... }
```

---

### sdk-canonical

Client for reading canonical football entities + shared DTOs.

```
CanonicalRef             — { type: Player|Team|Match|..., id: UUID }
CanonicalType            — enum of all canonical entity types
PlayerDTO                — read-only player identity (name, position, nationality, dob)
TeamDTO                  — read-only team identity
MatchDTO                 — read-only match identity (teams, date, competition)
TrainingSessionDTO       — read-only session identity (team, date, type)
CanonicalServiceClient   — HTTP client to fos-canonical-service (see methods below)
CanonicalResolver        — caches canonical lookups in Redis (optional, Phase 1+)
```

**CanonicalServiceClient methods:**
```java
PlayerDTO getPlayer(UUID id)
TeamDTO   getTeam(UUID id)
Optional<PlayerDTO> findByIdentity(String name, LocalDate dob, String nationality)  // for dedup in ingest
Optional<TeamDTO>   findTeamByName(String name, String country)                     // for dedup in ingest
```

Domains use `CanonicalRef` as a foreign key in their own entities. When they need to display the player's name, they call `CanonicalServiceClient.getPlayer(ref.id())`.

**Never** store `PlayerDTO` fields in a domain entity. Store only the `CanonicalRef`.

---

### sdk-test

Test utilities to avoid boilerplate in domain service tests.

```
FosTestContainersBase  — base test class that starts PostgreSQL + MongoDB + Kafka via Testcontainers
MockActorFactory       — builds test JWTs for different roles
SignalCaptor           — captures Kafka signals emitted during a test for assertion
MockCanonicalResolver  — returns test PlayerDTO/TeamDTO without calling canonical service
```

Every domain service test class extends `FosTestContainersBase`.

---

## Versioning and Dependency Rule

- The SDK is versioned with semantic versioning: `1.0.0`, `1.1.0`, etc.
- Domain services pin to a specific SDK version in their `pom.xml`
- SDK upgrades are tested against all domain services before release
- A domain service must never import a class from a governance service JAR — only from `fos-sdk`

---

## sdk-storage

File storage abstraction used directly by domain services. No separate storage HTTP service.

```
StoragePort              — interface: generateUploadUrl, generateDownloadUrl, confirmUpload, deleteObject
PresignedUploadUrl       — value object: uploadUrl, objectKey, expiresAt
MinioStorageAdapter      — active in dev (fos.storage.provider=minio)
S3StorageAdapter         — stub, completed before AWS production
AzureBlobStorageAdapter  — stub, completed before Azure production
NoopStorageAdapter       — default in tests and local dev without MinIO
```

Domain services that handle file uploads (fos-workspace-service, fos-ingest-service) depend on `sdk-storage`. They call `StoragePort` directly — never via HTTP to another service.

After a successful upload confirmation, the domain service emits an AUDIT signal to `fos.audit.all`. The signal package inside `fos-governance-service` consumes it and writes to `fos_audit` DB.

---

## Build Order Within the SDK

When building Phase 0:
```
sdk-core → sdk-events → sdk-security → sdk-storage → sdk-policy → sdk-canonical → sdk-test
```
Each module depends only on modules to its left.
