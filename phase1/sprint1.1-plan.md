# Sprint 1.1 Plan
## Phase 1 - Workspace Scaffold + Documents

## Goal
Deliver `fos-workspace-service` as a new Spring Boot module in the monorepo with MongoDB persistence, Mongock migrations, gateway routing, and a complete document upload lifecycle:
- initiate upload
- confirm upload
- get document
- list documents
- soft-delete document

All state-changing operations must emit signals through `FosKafkaProducer`.
All permission checks must go through `PolicyClient`.
All storage operations must go through `StoragePort`.

## Definition of Done
- [ ] `fos-workspace-service` exists in the monorepo and builds from the root
- [ ] service starts on port `8082`
- [ ] gateway routes workspace document endpoints correctly
- [ ] Mongock migration runs and creates document indexes
- [ ] document domain exists with version history and soft-delete behavior
- [ ] initiate upload returns a pre-signed URL and document id
- [ ] confirm upload transitions the document to `ACTIVE`
- [ ] get/list/delete document endpoints work
- [ ] OPA workspace document rules exist and match service action names
- [ ] integration tests cover the upload lifecycle
- [ ] no direct `MinioClient`, `MongoClient`, or direct governance service imports are used in workspace domain logic

## Non-Negotiable Constraints
- [ ] `fos-workspace-service` depends only on SDK modules, not on governance internals
- [ ] all Mongo persistence is through Spring Data MongoDB
- [ ] all Mongo structure/index creation is through Mongock only
- [ ] all storage access is through `StoragePort`
- [ ] all permission checks are through `PolicyClient`
- [ ] all state-changing operations emit FACT or AUDIT signals
- [ ] `WorkspaceDocument` extends `BaseDocument`
- [ ] documents use `CanonicalRef` for ownership and linked entities
- [ ] soft delete means `ARCHIVED`, never physical removal
- [ ] frontend upload flow remains backend-initiate -> direct upload -> backend-confirm

## Current Repo Alignment Notes
- [ ] use root version `0.1.0-SNAPSHOT`, not `1.0.0-SNAPSHOT`
- [ ] do not add frontend to Maven modules in this sprint
- [ ] MongoDB, MinIO, Kafka already exist in `docker-compose.yml`
- [ ] gateway currently has no workspace routes and must be updated
- [ ] local Testcontainers remains a known runtime risk and must be accounted for during test execution

## Deliverables
- [ ] root `pom.xml` updated with `fos-workspace-service`
- [ ] `fos-workspace-service/pom.xml`
- [ ] `WorkspaceApp`
- [ ] `application.yml`
- [ ] gateway route updates
- [ ] Mongock config
- [ ] `Migration001CreateDocumentIndexes`
- [ ] document domain classes
- [ ] document repository
- [ ] document DTOs
- [ ] `DocumentService`
- [ ] `DocumentController`
- [ ] `GlobalExceptionHandler`
- [ ] `workspace_policy.rego`
- [ ] `DocumentIntegrationTest`

## Execution Order

### 1. Register Module
- [ ] add `fos-workspace-service` to root `pom.xml`
- [ ] run a root Maven build to confirm the module is discovered
- [ ] expect temporary failure until the workspace module exists

### 2. Create Workspace Module
- [ ] create `fos-workspace-service/pom.xml`
- [ ] include SDK dependencies:
- [ ] `sdk-core`
- [ ] `sdk-events`
- [ ] `sdk-security`
- [ ] `sdk-storage`
- [ ] `sdk-policy`
- [ ] `sdk-canonical`
- [ ] include Spring Boot starters:
- [ ] web
- [ ] actuator
- [ ] validation
- [ ] data-mongodb
- [ ] oauth2-resource-server
- [ ] include Mongock dependencies
- [ ] include test dependencies:
- [ ] `sdk-test`
- [ ] Spring Boot test
- [ ] Testcontainers MongoDB
- [ ] Testcontainers JUnit
- [ ] WireMock
- [ ] run root build with tests skipped and confirm module compiles

### 3. Bootstrap Application
- [ ] create `WorkspaceApp`
- [ ] enable Mongock
- [ ] create `application.yml`
- [ ] configure:
- [ ] `server.port=8082`
- [ ] Mongo connection
- [ ] Kafka bootstrap servers
- [ ] policy service URL
- [ ] canonical service URL
- [ ] storage provider settings
- [ ] actuator health exposure
- [ ] verify service can boot structurally

### 4. Add Gateway Routing
- [ ] add workspace base URL property to gateway config
- [ ] route `/api/v1/documents/**` to `fos-workspace-service`
- [ ] keep routing style aligned with current gateway configuration
- [ ] verify no governance routes are broken

### 5. Add Mongock Migration Infrastructure
- [ ] create `MongockConfig`
- [ ] create `Migration001CreateDocumentIndexes`
- [ ] ensure the migration:
- [ ] creates `workspace_documents` if missing
- [ ] creates indexes for owner, category, linked player, state, createdAt, name
- [ ] verify migration scan package is correct
- [ ] verify Mongock changelog collection records execution

### 6. Implement Document Domain
- [ ] create `DocumentCategory`
- [ ] create `DocumentVisibility`
- [ ] create `DocumentVersion`
- [ ] create `WorkspaceDocument`
- [ ] ensure `WorkspaceDocument` extends `BaseDocument`
- [ ] implement aggregate behavior:
- [ ] create new document in `DRAFT`
- [ ] add version
- [ ] compute next version number
- [ ] transition `DRAFT -> ACTIVE` on first confirmed version
- [ ] soft-delete to `ARCHIVED`
- [ ] represent owner, linked player, and linked team using `CanonicalRef`

### 7. Implement Repository
- [ ] create `WorkspaceDocumentRepository`
- [ ] add queries for:
- [ ] owner + state
- [ ] category + state
- [ ] linked player + state
- [ ] resource id
- [ ] active existence check
- [ ] search by name
- [ ] keep repository method names aligned with Mongo field structure

### 8. Implement API DTOs
- [ ] create `InitiateUploadRequest`
- [ ] create `ConfirmUploadRequest`
- [ ] create `DocumentResponse`
- [ ] create a small upload initiation result DTO if needed
- [ ] keep response mapping out of controller logic where possible
- [ ] include version summary in `DocumentResponse`
- [ ] include generated short-lived download URL in `DocumentResponse`

### 9. Implement DocumentService
- [ ] add `initiateUpload`
- [ ] add `confirmUpload`
- [ ] add `getDocument`
- [ ] add `listDocuments`
- [ ] add `softDeleteDocument`
- [ ] in `initiateUpload`:
- [ ] resolve current actor from `FosSecurityContext`
- [ ] compute policy action name from category and operation
- [ ] call `PolicyClient`
- [ ] generate pre-signed upload URL through `StoragePort`
- [ ] create draft `WorkspaceDocument`
- [ ] persist draft document
- [ ] return `documentId`, `uploadUrl`, `objectKey`
- [ ] in `confirmUpload`:
- [ ] reload document by `resourceId`
- [ ] create `DocumentVersion`
- [ ] append version
- [ ] transition to `ACTIVE`
- [ ] save
- [ ] emit AUDIT signal
- [ ] emit FACT signal
- [ ] return response with download URL
- [ ] in read/delete methods:
- [ ] enforce permission check through `PolicyClient`
- [ ] never hardcode role checks in service logic

### 10. Implement Controller and Error Handling
- [ ] create `DocumentController`
- [ ] expose:
- [ ] `POST /api/v1/documents/upload/initiate`
- [ ] `POST /api/v1/documents/upload/confirm`
- [ ] `GET /api/v1/documents/{documentId}`
- [ ] `GET /api/v1/documents`
- [ ] `DELETE /api/v1/documents/{documentId}`
- [ ] keep controller thin
- [ ] create `GlobalExceptionHandler`
- [ ] map:
- [ ] validation errors -> `400`
- [ ] not found -> `404`
- [ ] forbidden -> `403`
- [ ] illegal state -> `409`

### 11. Add OPA Workspace Policy
- [ ] create `fos-governance-service/src/main/resources/opa/workspace_policy.rego`
- [ ] define rules for:
- [ ] general documents
- [ ] medical documents
- [ ] admin documents
- [ ] report documents
- [ ] contract documents
- [ ] make action names exactly match service-generated actions
- [ ] verify deny/allow behavior can support tests

### 12. Implement Integration Tests
- [ ] create `DocumentIntegrationTest`
- [ ] cover:
- [ ] initiate upload returns `201`
- [ ] confirm upload returns `200` and state becomes `ACTIVE`
- [ ] get document returns expected payload
- [ ] delete returns archive semantics
- [ ] use WireMock for `PolicyClient`
- [ ] use `NoopStorageAdapter`
- [ ] use Mongo + Kafka test infrastructure
- [ ] if Testcontainers is still blocked locally, decide one path before executing tests:
- [ ] fix Docker/Testcontainers first
- [ ] or provide a test fallback strategy for local services

### 13. Full Verification
- [ ] run `mvn -pl fos-workspace-service test`
- [ ] run root `mvn package`
- [ ] boot service locally
- [ ] verify `/actuator/health`
- [ ] verify Mongock changelog shows migration execution
- [ ] smoke test full initiate -> confirm -> get -> delete flow

## Strict Acceptance Checklist
- [ ] service reachable on `8082`
- [ ] gateway forwards workspace routes
- [ ] Mongo collection and indexes created by Mongock
- [ ] document first save is `DRAFT`
- [ ] first confirm makes document `ACTIVE`
- [ ] subsequent confirms create new versions
- [ ] soft delete sets `ARCHIVED`
- [ ] download URL generation uses `StoragePort`
- [ ] upload URL generation uses `StoragePort`
- [ ] all permission checks call `PolicyClient`
- [ ] all state-changing operations call `FosKafkaProducer`
- [ ] no direct MinIO SDK usage
- [ ] no direct governance service usage
- [ ] tests cover the upload lifecycle

## Known Risks
- [ ] Testcontainers on this machine may still fail due Docker named pipe issues
- [ ] generated sprint snippets in the phase doc may need adaptation to current repo conventions
- [ ] action names between service code and Rego may drift if not validated early
- [ ] gateway route omissions will make frontend/backend verification look broken even if service is healthy
- [ ] workspace app may need explicit component scanning if SDK beans are not auto-detected

## Risk Mitigations
- [ ] validate app context early with minimal bootstrap before full feature build
- [ ] validate SDK bean wiring early:
- [ ] `StoragePort`
- [ ] `PolicyClient`
- [ ] `FosKafkaProducer`
- [ ] add and verify gateway route before manual API testing
- [ ] keep upload flow metadata model simple and explicit
- [ ] run compile checks after each major layer:
- [ ] module scaffold
- [ ] Mongock
- [ ] domain/repository
- [ ] service/controller
- [ ] tests

## Out of Scope
- [ ] events/calendar
- [ ] player profile aggregation
- [ ] medical/admin profile tabs
- [ ] notifications inbox
- [ ] search endpoint
- [ ] OnlyOffice
- [ ] Angular frontend
- [ ] rate limiting changes
- [ ] websocket or push notifications

## Suggested Commit Boundaries
- [ ] commit 1: root module registration
- [ ] commit 2: workspace module `pom.xml`
- [ ] commit 3: app bootstrap + gateway route
- [ ] commit 4: Mongock config + migration
- [ ] commit 5: document domain
- [ ] commit 6: repository + DTOs
- [ ] commit 7: service
- [ ] commit 8: controller + exception handler
- [ ] commit 9: OPA policy
- [ ] commit 10: integration tests
- [ ] commit 11: final verification and cleanup

## Exit Criteria
Sprint 1.1 is complete only when:
- [ ] root build succeeds
- [ ] workspace service starts cleanly
- [ ] migration runs
- [ ] upload flow works end-to-end
- [ ] delete archives correctly
- [ ] tests pass or are blocked only by explicitly documented environment issues
- [ ] no architecture violations were introduced
