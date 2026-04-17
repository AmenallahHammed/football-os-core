# Football OS — Infrastructure

This document describes all infrastructure components: what each does, when it's needed, and how services connect to it.

---

## API Gateway

**Technology:** Spring Cloud Gateway (or Kong for production)

**Responsibilities:**
- Validate Keycloak JWT (signature + expiry) on every inbound request
- Route requests by path prefix to downstream services
- Inject `X-FOS-Request-Id` (correlation ID) on every request
- Rate limit: 100 req/min per actor (configurable)
- Forward actor claims in request headers to downstream services

**Path routing example:**
```
/api/identity/**    → fos-identity-service
/api/policy/**      → fos-policy-service
/api/canonical/**   → fos-canonical-service
/api/workspace/**   → fos-workspace-service
/api/dataperF/**    → fos-dataperF-service
```

**Start simple:** JWT validation + routing only. Rate limiting added in a later sprint.

---

## Keycloak 24

**Role:** External identity provider. Football OS does not build its own auth.

**How it integrates:**
- Actors log in via Keycloak (SSO, social login possible later)
- Keycloak issues JWTs; the gateway validates them
- When an actor is created/updated in Keycloak, a webhook fires → `fos-identity-service` syncs the actor
- Realm: one realm per club (multi-tenant) or one shared realm with groups (simpler to start)

**Start simple:** Single realm, local Keycloak in Docker for dev. Multi-tenant realm per club in a later phase.

---

## Apache Kafka

**Role:** All inter-service signals travel through Kafka.

**Topic naming convention:**
```
fos.{domain}.{entity}.{event}
Examples:
  fos.identity.actor.created
  fos.canonical.player.updated
  fos.workspace.file.uploaded
  fos.signal.escalation.raised
```

**Retention:** Standard 7-day retention. Audit topic: indefinite retention (or export to OpenSearch).

**Start simple:** Local Kafka in Docker (or Redpanda for lighter dev). Managed Kafka (MSK / Confluent) for production.

---

## PostgreSQL 16

**Role:** All governance core data (structured, transactional).

**Schemas** (one per service, same cluster for governance):
```
fos_identity      — actors, groups, memberships
fos_policy        — policy file metadata, escalation queue
fos_canonical     — football entity identities
fos_transmission  — file metadata, storage quotas
fos_signal        — notification state, escalation queue
fos_audit         — append-only audit log
```

**Migrations:** Flyway. Each service owns its schema. No cross-schema queries.

---

## MongoDB 7

**Role:** Domain service data (flexible schemas, document-oriented).

**One database per domain service:**
```
fos_workspace     — spaces, files, pages, events
fos_dataperF      — performance profiles, match stats
fos_matchview     — clips, annotations, playlists
fos_coachpad      — training plans, drill libraries
fos_profile       — form submissions, notifications
fos_ingest        — import jobs, connector configs
fos_warehouse     — analytics read models
```

**Start simple:** Single MongoDB instance in Docker. Replica set for production.

---

## File Storage

**Status: Provider not yet decided.** S3 (AWS) and Azure Blob Storage are both candidates. Do not write code that assumes either.

**Interface:** `StoragePort` (defined in `fos-transmission-service`)
**Adapters:**
- `MinioStorageAdapter` — for local dev (MinIO is S3-compatible, runs in Docker)
- `S3StorageAdapter` — for AWS production
- `AzureBlobStorageAdapter` — for Azure production

Active adapter selected via `fos.storage.provider` config property (`minio` / `s3` / `azure`).

All file operations go through `fos-transmission-service`. Domain services never write directly to storage — they request a pre-signed URL from transmission, which delegates to `StoragePort`.

---

## Redis

**Role:** Caching layer (optional in early phases).

**Used for:**
- `CanonicalResolver` — caches `PlayerDTO` / `TeamDTO` lookups
- Session/token cache (if needed)

**Start simple:** Skip Redis entirely in Phase 0. Add when canonical lookup latency becomes an issue.

---

## OPA (Open Policy Agent)

**Role:** Policy evaluation engine.

**Deployment:** Sidecar container alongside `fos-policy-service`.
- Loads Rego policy files from `fos-policy-service` at startup
- Called via localhost HTTP: `POST http://localhost:8181/v1/data/fos/allow`
- `fos-policy-service` owns all Rego files and manages OPA reloads

Domain services never call OPA directly — always via `PolicyClient` from the SDK.

---

## Search / Audit Logs

**Status: Provider not yet decided.** OpenSearch and Elasticsearch are both candidates.

**Interface:** `SearchPort` (defined in `fos-transmission-service` for audit; per-domain service for domain search)
**Adapters:**
- `OpenSearchAdapter`
- `ElasticsearchAdapter`

Active adapter selected via `fos.search.provider` config property (`opensearch` / `elasticsearch`).

**What feeds it:**
- Kafka consumer reads `fos.audit.*` topics and indexes via `SearchPort`
- All audit signals end up searchable here

**Start simple:** Add search in Phase 1 for audit compliance. Domain-level search (e.g., player search in DataPerf) added per domain when needed.

---

## Local Development Stack (docker-compose)

All of the above runs locally via docker-compose:

```yaml
services:
  keycloak      # port 8080
  postgres      # port 5432
  mongodb       # port 27017
  kafka         # port 9092
  zookeeper     # internal
  minio         # port 9000 (S3 API), 9001 (console)
  redis         # port 6379 (added when needed)
  opensearch    # port 9200
  opa           # sidecar, not standalone
```

Every service repo has a `docker-compose.yml` for its own dependencies and a root-level compose for the full stack.
