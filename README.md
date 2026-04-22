# Football OS Core Documentation

## 1) Purpose

This repository contains the core backend platform for Football OS.

It combines:

1. Shared SDK modules
2. Governance domain service
3. Workspace domain service
4. API gateway
5. Local infrastructure orchestration

## 2) High-level architecture

Backend request path:

1. Client calls `fos-gateway` (`:8080`).
2. Gateway validates JWT, enriches headers, applies rate limit, and routes traffic.
3. Domain requests go to:
   - `fos-governance-service` (identity, canonical, policy, signal, audit)
   - `fos-workspace-service` (documents, events, profiles, notifications, search, onlyoffice)
4. Services emit/consume Kafka signals and persist domain state.

Core data stores:

- PostgreSQL (governance)
- MongoDB (workspace)
- Kafka (events/signals)
- Redis (gateway rate limiting)
- MinIO (workspace document objects)

Supporting services:

- Keycloak (JWT/JWKS)
- OPA (policy decisions)
- OnlyOffice (document editing)
- OpenSearch (provisioned for future/extended search use)

## 3) Repository modules

Root Maven modules (from `pom.xml`):

- `fos-sdk`
- `fos-governance-service`
- `fos-gateway`
- `fos-workspace-service`

Main module docs:

- `fos-sdk/README.md`
- `fos-governance-service/README.md`
- `fos-workspace-service/README.md`
- `fos-gateway/README.md`

## 4) Service boundaries and responsibilities

- `fos-sdk`
  - Shared contracts, adapters, policy/canonical clients, event primitives, and test utilities.

- `fos-governance-service`
  - Owns identity/canonical/policy/audit concerns and governance schemas.

- `fos-workspace-service`
  - Owns workspace document/event/notification aggregates and workspace APIs.

- `fos-gateway`
  - Edge enforcement: auth, request enrichment, rate limiting, and routing.

## 5) Integration map

Service-to-service:

- Workspace -> Governance policy endpoint (`sdk-policy`)
- Workspace -> Governance canonical endpoint (`sdk-canonical`)
- Gateway -> Governance/Workspace downstream routing

Event-driven:

- Services use `sdk-events` contracts (`SignalEnvelope`, `KafkaTopics`)
- Governance writes audit records from audit topics
- Workspace consumes workspace topics to materialize inbox notifications

## 6) Quick Start / Run Locally

Prerequisites:

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### 6.1 Prepare env

From repo root, create `.env` from example and set required secrets:

- `KEYCLOAK_ADMIN`
- `KEYCLOAK_ADMIN_PASSWORD`
- `OPENSEARCH_INITIAL_ADMIN_PASSWORD`

Reference file: `.env.example`

### 6.2 Start infrastructure

```bash
docker compose up -d
```

This starts Postgres, MongoDB, Kafka, Keycloak, MinIO, OpenSearch, Redis, and OnlyOffice.

### 6.3 Build project

```bash
mvn clean install -DskipTests
```

### 6.4 Run services (recommended order)

1) Governance service (set port 8081 to match gateway defaults):

```bash
mvn -pl fos-governance-service -am spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

2) Workspace service (defaults to 8082):

```bash
mvn -pl fos-workspace-service -am spring-boot:run
```

3) Gateway (defaults to 8080):

```bash
mvn -pl fos-gateway -am spring-boot:run
```

### 6.5 Smoke check

- Gateway health: `http://localhost:8080/actuator/health`
- Governance health: `http://localhost:8081/actuator/health`
- Workspace health: `http://localhost:8082/actuator/health`

## 7) Testing

Run all tests:

```bash
mvn test
```

Run per module:

```bash
mvn -pl fos-governance-service test
mvn -pl fos-workspace-service test
mvn -pl fos-gateway test
mvn -pl fos-sdk -am test
```

## 8) Practical mental model

Think of the platform in three layers:

1. Edge (`fos-gateway`) - protect and route requests
2. Domain services (`fos-governance-service`, `fos-workspace-service`) - own business workflows and persistence
3. Shared foundation (`fos-sdk`) - unify contracts, integration patterns, and test support
