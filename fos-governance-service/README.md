# fos-governance-service Documentation

## 1) Purpose

`fos-governance-service` is the governance core of Football OS.

It owns and exposes:

1. Identity governance (`Actor` lifecycle + Keycloak sync)
2. Canonical entities (`Player`, `Team`)
3. Policy evaluation (OPA-backed allow/deny decisions)
4. Signal intake/pipeline and append-only audit logging

## 2) Runtime role in the backend

Typical runtime flow:

1. HTTP requests hit controllers in `identity`, `canonical`, `policy`, or `signal` APIs.
2. Services enforce business rules and orchestrate persistence + event emission.
3. JPA repositories persist entities into PostgreSQL schemas owned by governance.
4. Policy endpoint builds policy context and calls OPA.
5. Signal endpoint runs a Chain of Responsibility pipeline.
6. Kafka consumer writes immutable audit rows into `fos_audit.audit_log`.

Cross-cutting behavior:

- JWT resource-server security when `fos.security.enabled=true`
- Flyway database migrations at startup
- Global exception-to-error-response mapping
- JPA auditing (`createdAt`, `updatedAt`, `version`)

## 3) Folder structure

```text
fos-governance-service/
  pom.xml
  src/
    main/
      java/com/fos/governance/
        GovernanceApp.java
        config/
        identity/
        canonical/
        policy/
        signal/
      resources/
        application.yml
        db/migration/
        opa/
    test/java/com/fos/governance/
      identity/
      canonical/
      policy/
      signal/
      e2e/
```

## 4) Data/model interaction map

Owned backend models:

- `Actor` -> `fos_identity.actor`
- `Player` -> `fos_canonical.player`
- `Team` -> `fos_canonical.team`
- `AuditLogEntry` -> `fos_audit.audit_log`

How layers interact with these models:

- `api` layer validates request contracts and returns response contracts.
- `application` layer performs domain orchestration and emits Kafka signals.
- `domain` layer keeps lifecycle methods and invariants.
- `infrastructure.persistence` repositories provide CRUD/query access.
- `resources/db/migration` defines physical schema/table contracts.

Event interaction around models:

- Actor lifecycle emits `KafkaTopics.IDENTITY_*`
- Canonical lifecycle emits `KafkaTopics.CANONICAL_*`
- Signal processing forwards events and optionally sends alerts
- Audit consumer persists all relevant audit events from `KafkaTopics.AUDIT_ALL`

## 5) Package responsibilities

- `config`: security, global exception mapping, policy chain wiring, signal pipeline wiring
- `identity`: actor role/domain model, actor API/service, Keycloak webhook bridge
- `canonical`: player/team domain model + CRUD/find APIs/services
- `policy`: policy request API + OPA input building + OPA HTTP adapter
- `signal`: signal intake API, processing handlers, notification port, audit consumer
- `resources/opa`: Rego policy files used by OPA decisions
- `test`: unit/integration/e2e coverage of governance workflows

## 6) Key files and what they do

- `fos-governance-service/src/main/java/com/fos/governance/GovernanceApp.java`
  - Spring Boot bootstrap for governance context and JPA auditing.

- `fos-governance-service/src/main/java/com/fos/governance/config/SecurityConfig.java`
  - Enables JWT auth for all business endpoints (except health/info) when security is enabled.

- `fos-governance-service/src/main/java/com/fos/governance/config/SignalPipelineConfig.java`
  - Assembles signal processing chain:
    `SchemaValidation -> ActorEnrichment -> TypeClassification -> KafkaRouting -> NotificationFanOut`.

- `fos-governance-service/src/main/java/com/fos/governance/identity/application/ActorService.java`
  - Main identity service for create/get/update/deactivate + Keycloak ID sync.

- `fos-governance-service/src/main/java/com/fos/governance/canonical/application/PlayerService.java`
  - Canonical player create/get/update/find and event emission.

- `fos-governance-service/src/main/java/com/fos/governance/canonical/application/TeamService.java`
  - Canonical team create/get/update/find and event emission.

- `fos-governance-service/src/main/java/com/fos/governance/policy/application/context/OpaEvaluator.java`
  - Builds OPA input context and translates OPA response to `PolicyResult`.

- `fos-governance-service/src/main/java/com/fos/governance/policy/infrastructure/opa/OpaClient.java`
  - HTTP client to OPA endpoint `/v1/data/fos/allow`.

- `fos-governance-service/src/main/java/com/fos/governance/signal/infrastructure/audit/AuditLogConsumer.java`
  - Kafka consumer that idempotently persists audit rows.

- `fos-governance-service/src/main/resources/db/migration/V001__create_schemas.sql`
  - Creates governance-owned schemas (`fos_identity`, `fos_canonical`, `fos_policy`, `fos_signal`, `fos_audit`).

- `fos-governance-service/src/main/resources/opa/fos_policy.rego`
  - Base allow/deny rules by actor role, action, and resource state.

## 7) Integrations with other backend components

- PostgreSQL for persistence (identity/canonical/audit)
- Kafka for lifecycle and governance signals
- OPA sidecar/service for authorization decisions
- Keycloak for identity-source events
- Shared SDK contracts (`sdk-core`, `sdk-events`, `sdk-policy`, `sdk-canonical`, `sdk-security`)

## 8) Practical mental model

Treat governance as the backend control plane:

1. Own critical identity/canonical state
2. Decide authorization
3. Normalize governance events
4. Keep immutable audit history

## 9) Quick Start / Run Locally

Prerequisites:

- Java 21
- Maven 3.9+
- PostgreSQL and Kafka running
- OPA running at `http://localhost:8181` (or set `fos.opa.url`)

Required environment variables:

- `KEYCLOAK_WEBHOOK_SECRET` (mandatory for webhook controller)

Useful optional environment variables:

- `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `KEYCLOAK_JWKS_URI`
- `FOS_SECURITY_ENABLED` (`false` for local no-auth testing)

Run from repository root:

```bash
mvn -pl fos-governance-service -am spring-boot:run
```

Run tests:

```bash
mvn -pl fos-governance-service test
```
