# fos-sdk Documentation

## 1) Purpose

`fos-sdk` is the shared backend SDK for Football OS services.

It standardizes contracts and integrations so service modules do not duplicate core plumbing.

SDK submodules:

1. `sdk-core`
2. `sdk-events`
3. `sdk-security`
4. `sdk-storage`
5. `sdk-canonical`
6. `sdk-policy`
7. `sdk-test`

## 2) Runtime role in the backend

Services use this SDK as a toolbox:

1. Common state/response primitives from `sdk-core`
2. Kafka signal contracts + producer/consumer templates from `sdk-events`
3. JWT actor context + audit annotation hooks from `sdk-security`
4. Storage provider abstraction from `sdk-storage`
5. Canonical lookup client/cache from `sdk-canonical`
6. Policy client + declarative method guard from `sdk-policy`
7. Integration-test infrastructure from `sdk-test`

The SDK does not own business tables. It defines reusable contracts for services that own business data.

## 3) Folder structure

```text
fos-sdk/
  pom.xml
  sdk-core/
  sdk-events/
  sdk-security/
  sdk-storage/
  sdk-canonical/
  sdk-policy/
  sdk-test/
```

## 4) Data/model interaction map

How SDK modules interact with backend models:

- Identity context interaction: JWT claims (`actorId`, roles, clubId)
- Canonical model interaction: canonical refs and DTO reads (`PlayerDTO`, `TeamDTO`)
- Policy model interaction: `PolicyRequest`/`PolicyResult` remote evaluation
- Event/audit interaction: shared `SignalEnvelope` and topic contracts
- Storage interaction: bucket/object-key contracts through `StoragePort`

Ownership boundary:

- Services (for example governance/workspace) own persistent domain models.
- SDK provides shared contracts and integration mechanisms around those models.

## 5) Module responsibilities

### `sdk-core`

Concern: shared primitives and lifecycle rules.

- `ResourceState`, `FosRoles`, `ErrorResponse`, `PageResponse`
- `BaseEntity` and `BaseDocument`
- State handler strategy/factory (`Draft`, `Active`, `Archived`)

### `sdk-events`

Concern: shared event envelope and Kafka patterns.

- `SignalEnvelope`, `SignalType`, `KafkaTopics`
- `RequestContext` correlation propagation
- `FosKafkaProducer` enrichment wrapper
- `AbstractFosConsumer` template consumer flow

### `sdk-security`

Concern: auth context and audit hooks.

- `FosSecurityContext` (actor, role, club, role checks)
- `FosJwtConverter` (roles claim -> authorities)
- `@Audited` + `AuditAspect` (emit audit signal)

### `sdk-storage`

Concern: object storage abstraction.

- `StoragePort` + `PresignedUploadUrl`
- Provider adapters: `Noop`, `Minio`, `S3` (placeholder), `Azure` (placeholder)
- `StorageAutoConfiguration`

### `sdk-canonical`

Concern: canonical references and canonical API integration.

- `CanonicalType`, `CanonicalRef`
- `PlayerDTO`, `TeamDTO`
- `CanonicalServiceClient` (remote governance canonical API)
- `CanonicalResolver` caching proxy + evict support
- `CanonicalAutoConfiguration`

### `sdk-policy`

Concern: policy contract and declarative enforcement.

- `PolicyRequest`, `PolicyResult`, `PolicyDecision`
- `PolicyClient` (remote governance policy API)
- `PolicyResultCache` (Caffeine)
- `@PolicyGuard` + `PolicyGuardAspect`
- `PolicyAutoConfiguration`

### `sdk-test`

Concern: reusable integration test infrastructure.

- `FosTestContainersBase` (Postgres/Kafka test runtime)
- `MockActorFactory`, `TestActor`
- `SignalCaptor`
- `MockCanonicalResolver`

## 6) Key files and what they do

- `fos-sdk/pom.xml`
  - Aggregates all SDK submodules.

- `fos-sdk/sdk-core/src/main/java/com/fos/sdk/core/BaseEntity.java`
  - Shared JPA superclass (`resourceId`, `state`, `version`, timestamps).

- `fos-sdk/sdk-events/src/main/java/com/fos/sdk/events/SignalEnvelope.java`
  - Canonical event payload contract used across backend services.

- `fos-sdk/sdk-events/src/main/java/com/fos/sdk/events/KafkaTopics.java`
  - Central topic registry to avoid topic-name drift.

- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java`
  - Standard way to read current authenticated actor context.

- `fos-sdk/sdk-storage/src/main/java/com/fos/sdk/storage/StoragePort.java`
  - Provider-agnostic storage contract for upload/download and lifecycle actions.

- `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalResolver.java`
  - Caching canonical read facade for domain services.

- `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyGuardAspect.java`
  - Enforces policy checks around annotated methods.

- `fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/FosTestContainersBase.java`
  - Shared integration-test baseline for backend modules.

Auto-configuration exports:

- `fos-sdk/sdk-storage/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `fos-sdk/sdk-canonical/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `fos-sdk/sdk-policy/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 7) Integrations with other backend components

- Governance service: canonical reads + policy decisions + audit/event contracts
- Workspace and other domain services: storage, policy, canonical, security, and event plumbing
- Kafka, JWT resource-server stack, RestClient-based service-to-service calls, and test container stack

## 8) Practical mental model

Treat `fos-sdk` as the backend foundation layer:

1. Shared contracts
2. Shared integration adapters
3. Shared enforcement hooks
4. Shared test infrastructure

Services stay domain-focused while SDK provides consistent cross-service behavior.

## 9) Quick Start / Run Locally

Prerequisites:

- Java 21
- Maven 3.9+

Build SDK modules from repository root:

```bash
mvn -pl fos-sdk -am install
```

Run SDK tests:

```bash
mvn -pl fos-sdk -am test
```

Use SDK modules in a service `pom.xml` (example):

```xml
<dependency>
  <groupId>com.fos</groupId>
  <artifactId>sdk-policy</artifactId>
  <version>${fos.sdk.version}</version>
</dependency>
```

Spring Boot auto-config modules exported by SDK:

- `sdk-storage`
- `sdk-canonical`
- `sdk-policy`
