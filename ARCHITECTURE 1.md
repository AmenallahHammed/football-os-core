# Football OS — Architecture

## Three-Layer Onion

```
┌─────────────────────────────────────────────────┐
│                   DOMAINS                        │
│  (Workspace, DataPerf, Match View, Coach Pad,    │
│   Profile, Scouting, Admin, Ingest)              │
│                    ↓ uses                        │
├─────────────────────────────────────────────────┤
│                   fos-sdk                        │
│  sdk-core, sdk-events, sdk-security,            │
│  sdk-policy, sdk-canonical, sdk-storage,        │
│  sdk-test                                       │
│                    ↓ talks to                    │
├─────────────────────────────────────────────────┤
│              GOVERNANCE CORE                     │
│  fos-governance-service                         │
│  (identity · canonical · policy · signal)       │
│  + fos-gateway                                  │
└─────────────────────────────────────────────────┘
```

**The absolute rule:** Domains depend on `fos-sdk` only. They never import or HTTP-call governance core service classes directly. The SDK is the contract boundary.

**File storage is NOT in governance.** `sdk-storage` is a library that domain services use directly — no HTTP hop to a separate storage service. This keeps heavy I/O co-located with the domain that owns it.

---

## The Resource Pattern

Every piece of data in every domain follows this base structure:

```
Resource {
  resource_id : UUID          // globally unique
  owner_ref   : CanonicalRef  // who owns this (Actor or Team)
  state       : draft | active | archived
  version     : integer       // optimistic locking
  created_at  : timestamp
  updated_at  : timestamp
}
```

All domain entities extend this. It enables uniform state management, ownership policies, and archiving across every app.

---

## The Signal Pattern

All inter-service communication follows a typed envelope. No free-form REST calls between services for business events.

```
SignalEnvelope {
  signal_id     : UUID
  type          : intent | fact | alert | audit
  topic         : string           // e.g. "player.form.submitted"
  payload       : object           // domain-specific
  actor_ref     : CanonicalRef     // who triggered it
  correlation_id: string           // X-FOS-Request-Id from gateway
  timestamp     : timestamp
}
```

| Signal Type | Meaning | Example |
|-------------|---------|---------|
| `intent`    | Request to do something | "Assign training to player" |
| `fact`      | Something happened, notify others | "Player submitted daily form" |
| `alert`     | Needs human attention | "Player missed 3 sessions" |
| `audit`     | Immutable log, never deleted | "User accessed medical file" |

---

## The Canonical Ref Pattern

The canonical service holds the **identity facts** of football entities. All domains reference them by ID — they never copy or replicate canonical data.

```
CanonicalRef {
  type : Player | Team | Match | TrainingSession | Club
  id   : UUID
}
```

**In a domain entity:**
```java
// DataPerf domain
class PlayerPerformanceProfile extends BaseResource {
    CanonicalRef playerRef;   // "Player-123" — never name/position/DOB
    int goals;
    double xG;
    // ... domain-specific analytics fields
}
```

**Resolving a ref:** Use `CanonicalServiceClient` from `sdk-canonical`. It caches via Redis. Never query canonical DB directly from a domain.

---

## Design Patterns

Football OS applies specific design patterns at the architecture level and at the per-service level. The full catalog — with justification, code sketches, and per-service applicability — is in `DESIGN-PATTERNS.md`. Read it alongside this file for any planning session.

### Architecture-level patterns applied here

| Pattern | Where | Why |
|---------|-------|-----|
| **Adapter** (Port & Adapter) | External vendors (storage, search, notifications) | Storage and search providers not yet decided; adapters let us swap without touching business logic |
| **Proxy** | `PolicyClient`, `CanonicalResolver` in SDK | Remote and caching proxies hide network latency and complexity from domain code |
| **Template Method** | `AbstractFosConsumer` in sdk-events | Enforces Kafka consumer structure; subclasses only implement domain logic |
| **Decorator** | `FosKafkaProducer` in sdk-events | Injects correlation ID and timestamps transparently on every signal |
| **State** | `BaseEntity` / `BaseDocument` in sdk-core | Resource state (DRAFT→ACTIVE→ARCHIVED) drives allowed operations — encapsulated in state objects, not `if` chains |
| **Facade** | Each SDK module | Hides governance service complexity behind a simple client interface |

### Config property convention for Adapters

```yaml
fos:
  storage:
    provider: minio       # minio | s3 | azure
  notification:
    push-provider: noop   # noop | firebase
    email-provider: noop  # noop | ses | sendgrid
  search:
    provider: opensearch  # opensearch | elasticsearch
```

`noop` is always the default in dev. Real adapters are activated in staging/production.

---

## ABAC Policy Model

Access control is **Attribute-Based** (ABAC), not just role-based.

A policy decision considers:
- **Who** is asking (actor + roles)
- **What** they want to do (action)
- **What** they want to access (resource + its state + its owner)
- **Context** (club membership, team assignment, time-based rules)

Policy evaluation is handled by OPA (Open Policy Agent) via the policy package inside `fos-governance-service`. Domains call `PolicyClient` from the SDK — they never write their own permission logic.

---

## Request Lifecycle

```
Client → Gateway (JWT validate, rate limit, inject X-FOS-Request-Id)
       → Domain Service (extract actor from JWT, call PolicyClient)
       → PolicyClient → fos-governance-service/policy → OPA sidecar → ALLOW / DENY
       → Business logic → emit Signal (Kafka) if state changed
       → Response
```

For file operations (Workspace, Ingest):
```
Domain Service → sdk-storage (StoragePort) → MinioStorageAdapter / S3 / Azure
              → emits AUDIT signal to fos.audit.all Kafka topic
              → fos-governance-service/signal consumes → writes to fos_audit DB
```

---

## Technology Choices

| Concern | Technology | Notes |
|---------|-----------|-------|
| Backend services | Java 21 + Spring Boot 3.x | All governance + domain services |
| Frontend apps | Angular 17+ | All web apps |
| Mobile (Profile) | TBD (React Native or Flutter) | Phase 5 |
| ETL connectors | Python 3.x | fos-ingest connectors only |
| Identity | Keycloak 24 | External; governance wraps it |
| Policy engine | OPA (sidecar) | Runs alongside fos-governance-service |
| Event bus | Apache Kafka | All signals route through Kafka |
| Relational DB | PostgreSQL 16 | Governance schemas (fos_identity, fos_canonical, fos_policy, fos_signal, fos_audit) |
| Document DB | MongoDB 7 | Domain services |
| Cache | Redis | Rate limiting (gateway) + canonical ref cache (Phase 1+) |
| File storage | **TBD** — S3 or Azure Blob / MinIO (dev) | Via `StoragePort` in `sdk-storage` — used directly by domain services |
| Search / Audit logs | **TBD** — OpenSearch or Elasticsearch | Via `SearchPort`, provider selectable |
| API Gateway | Spring Cloud Gateway | JWT filter, Redis rate limiting, routing |
