# Football OS — Governance Core

The Governance Core knows nothing about football. It provides the platform primitives that every domain uses: who you are, what you can do, what happened, and which football entities exist.

No domain service touches this layer directly. All access is via `fos-sdk`.

---

## Architecture Decision: One Service, Not Five

All governance concerns (identity, policy, canonical data, signal routing) are implemented as packages inside a **single deployable service: `fos-governance-service`**. They share one PostgreSQL cluster, one port, and one deployment unit.

**Why:**
- These four concerns scale together. More actors = more policy checks = more canonical lookups. There is no independent scaling argument for separating them.
- Running four separate Spring Boot apps for what is essentially user/roles/config data is resource-expensive and operationally complex without benefit.
- File storage (previously `fos-transmission-service`) does NOT belong in governance. I/O happens inside the domains that own the files (Workspace, Ingest). Storage access is provided via `sdk-storage` — a shared library that domain services use directly.

---

## fos-governance-service

**One Spring Boot application. Four internal packages. One PostgreSQL cluster.**

Port: `8081`

```
com.fos.governance/
├── identity/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/persistence/
├── canonical/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/persistence/
├── policy/
│   ├── api/
│   ├── application/
│   ├── domain/pipeline/
│   └── infrastructure/opa/
├── signal/
│   ├── api/
│   ├── application/
│   ├── domain/pipeline/
│   └── infrastructure/notification/
└── config/
```

### Identity package

**Responsibility:** Actor lifecycle and group membership.

**What it does:**
- CRUD for Actors (create, update, deactivate)
- Syncs with Keycloak via webhook (actor created in Keycloak → event → stored)
- Publishes `ActorCreated`, `RoleAssigned`, `ActorDeactivated` FACT signals to Kafka

**Database:** PostgreSQL schema `fos_identity`

**Start simple:** Actor CRUD + Keycloak sync. Groups and membership in a later sprint.

---

### Canonical package

**Responsibility:** Single source of truth for football entity identities.

**What it does:**
- Stores identity facts for: Player, Team, Match, TrainingSession, Club
- Provides read API (by ID, by name search, by team)
- Publishes `PlayerCreated`, `TeamUpdated`, etc. FACT signals to Kafka
- Does NOT store domain-specific data (no stats, no documents, no forms)

**Database:** PostgreSQL schema `fos_canonical`

**Canonical entities (minimal):**
```
Player:          player_id, name, position, nationality, date_of_birth, current_team_ref
Team:            team_id, name, short_name, country, club_ref
Match:           match_id, home_team_ref, away_team_ref, scheduled_at, competition
TrainingSession: session_id, team_ref, scheduled_at, type
Club:            club_id, name, country, tier
```

**Start simple:** Player + Team only. Match and TrainingSession added when a domain needs them.

---

### Policy package

**Responsibility:** Evaluate access control decisions. Returns ALLOW / DENY.

**What it does:**
- Hosts Rego policy files per domain
- Delegates evaluation to an OPA sidecar (localhost HTTP at `8181`)
- Receives `PolicyRequest` from domains via SDK client
- Returns `PolicyResult` with decision + reason
- ESCALATE queue deferred to a later sprint

**Database:** PostgreSQL schema `fos_policy` (policy file metadata)

**OPA sidecar:** Runs as a separate Docker container alongside `fos-governance-service`. Called at `http://localhost:8181/v1/data/fos/allow`.

**Start simple:** Basic ALLOW/DENY on role + resource state. Fine-grained ABAC in later sprints.

---

### Signal package

**Responsibility:** Signal intake, routing to Kafka, and notification fan-out.

**What it does:**
- Receives signals from all services and routes them to Kafka topics
- Processes signals through a Chain of Responsibility pipeline
- Fan-out: sends notifications to relevant actors (ALERT signals only; push/email via `NotificationPort`)
- ESCALATE queue deferred

**Database:** PostgreSQL schema `fos_signal` (notification state)

**Start simple:** INTENT, FACT, ALERT route to Kafka. NotificationPort uses `NoopNotificationAdapter` in dev.

---

## PostgreSQL Schema Layout

```
PostgreSQL 16 (one cluster for governance)
├── fos_identity     — actors, groups, memberships
├── fos_policy       — policy files, escalation queue (future)
├── fos_canonical    — football entity identities
├── fos_signal       — notification state
└── fos_audit        — append-only audit log (written by domain services)
```

All schemas use Flyway for migrations. Each package owns its schema exclusively — no cross-schema queries between packages.

---

## File Storage — NOT in Governance

File storage has been moved out of governance entirely.

**`sdk-storage`** is a shared library module inside `fos-sdk`. Domain services that need file operations (Workspace, Ingest) depend on `sdk-storage` directly. There is no separate HTTP service for pre-signed URLs.

```
sdk-storage/
├── StoragePort.java              — interface (the contract)
├── PresignedUploadUrl.java       — value object
├── MinioStorageAdapter.java      — active in dev
├── S3StorageAdapter.java         — stub (completed before AWS production)
├── AzureBlobStorageAdapter.java  — stub (completed before Azure production)
└── NoopStorageAdapter.java       — default in tests
```

Active adapter selected via `fos.storage.provider` config property in each domain service.

**Audit log** — each domain service emits AUDIT signals to `fos.audit.all` Kafka topic using `FosKafkaProducer`. The governance signal package consumes this topic and writes to `fos_audit`. Domain services do not write to `fos_audit` directly.

---

## What Governance Core Does NOT Do

- Does not know about match statistics, player performance, training plans, or any football-specific business logic
- Does not store documents, video clips, or any file content — that belongs to the domains
- Does not render any UI
- Does not call domain services
- Does not own file storage or pre-signed URL generation
