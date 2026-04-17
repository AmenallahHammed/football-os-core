# Sprint 1.3 Plan
## Phase 1 - Player Profiles + Medical/Admin Documents + OnlyOffice Config

## Goal
Extend `fos-workspace-service` with:
- player profile aggregation API
- role-gated profile tabs
- medical/admin document rule refinement
- OnlyOffice configuration endpoint
- OnlyOffice runtime integration in local stack
- Mongock migration for player-profile query optimization

This sprint is mostly an extension of the document domain from Sprint 1.1, not a new storage model.

## Hard Prerequisite Gate
Sprint 1.3 must not start until these are stable:
- [ ] Sprint 1.1 foundation exists
- [ ] Sprint 1.2 event/domain foundation exists if profile links to event-created docs later
- [ ] `fos-workspace-service` builds and boots
- [ ] workspace OPA policy file already exists
- [ ] workspace document flows are working
- [ ] `WorkspaceDocumentRepository` is available and queryable
- [ ] gateway routing for workspace APIs exists

## Definition of Done
- [ ] OnlyOffice service is available in local stack
- [ ] workspace service has OnlyOffice config properties
- [ ] workspace pom includes JWT signing dependencies
- [ ] `Migration003AddPlayerProfileIndex` runs successfully
- [ ] `PlayerProfileService` aggregates documents by category for one player
- [ ] role-gated tabs are enforced via `PolicyClient`
- [ ] `CanonicalResolver` is used for player identity header data
- [ ] OnlyOffice config endpoint generates signed config payloads
- [ ] medical/admin edit rules exist in OPA
- [ ] integration tests cover profile aggregation and OnlyOffice config generation

## Non-Negotiable Constraints
- [ ] do not create a separate player-profile Mongo collection
- [ ] reuse `workspace_documents`
- [ ] profile API is read-only aggregation
- [ ] document access remains policy-driven
- [ ] backend only generates OnlyOffice config; it does not proxy editing traffic
- [ ] backend only talks to storage through `StoragePort`
- [ ] player identity comes through `CanonicalResolver`
- [ ] tab visibility is represented by null/empty sections, not ad-hoc frontend-only hiding
- [ ] medical/admin behavior is expressed by category + policy, not duplicated storage models

## Current Repo Alignment Notes
- [ ] OnlyOffice is not yet present in current `docker-compose.yml`
- [ ] `.env.example` will need OnlyOffice variables once implementation starts
- [ ] workspace module is still a prerequisite dependency
- [ ] current repo version remains `0.1.0-SNAPSHOT`
- [ ] local Testcontainers/Docker instability is still a known risk for integration tests

## Deliverables
- [ ] OnlyOffice service entry in `docker-compose.yml`
- [ ] OnlyOffice env vars in `.env.example`
- [ ] workspace `application.yml` OnlyOffice config
- [ ] JJWT dependencies in workspace pom
- [ ] `Migration003AddPlayerProfileIndex`
- [ ] `PlayerProfileResponse`
- [ ] `PlayerProfileService`
- [ ] `PlayerProfileController`
- [ ] `OnlyOfficeConfigRequest`
- [ ] `OnlyOfficeConfigResponse`
- [ ] `OnlyOfficeConfigService`
- [ ] `OnlyOfficeController`
- [ ] OPA profile/medical/admin edit rules
- [ ] `PlayerProfileIntegrationTest`
- [ ] `OnlyOfficeConfigTest`

## Execution Order

### 0. Prerequisite Validation
- [ ] verify Sprint 1.1 document flow is in place
- [ ] verify workspace service starts
- [ ] verify workspace OPA policy file exists
- [ ] verify `WorkspaceDocumentRepository` supports linked-player lookups
- [ ] stop if workspace service foundation is not real yet

### 1. Add OnlyOffice Runtime Support
- [ ] add OnlyOffice service to `docker-compose.yml`
- [ ] add OnlyOffice data volume
- [ ] add `.env.example` variables:
- [ ] `ONLYOFFICE_JWT_SECRET`
- [ ] `ONLYOFFICE_URL`
- [ ] add OnlyOffice config section to workspace `application.yml`
- [ ] add JJWT dependencies to workspace pom

### 1.1 OnlyOffice Runtime Rules
- [ ] local service exposed on `8090`
- [ ] JWT must be enabled
- [ ] secret must come from env/config
- [ ] healthcheck must be realistic for the container image
- [ ] config values must not be hardcoded in service classes

### 2. Add Migration003
- [ ] create `Migration003AddPlayerProfileIndex`
- [ ] add compound index on:
- [ ] `linkedPlayerRef.id`
- [ ] `category`
- [ ] `state`
- [ ] keep migration order as `003`
- [ ] verify rollback path is present

### 3. Implement Player Profile API
- [ ] create `PlayerProfileResponse`
- [ ] create `PlayerProfileService`
- [ ] create `PlayerProfileController`

### 3.1 Player Profile Service Rules
- [ ] fetch player identity via `CanonicalResolver`
- [ ] check tab access using `PolicyClient`
- [ ] evaluate separately for:
- [ ] `workspace.profile.tab.documents`
- [ ] `workspace.profile.tab.reports`
- [ ] `workspace.profile.tab.medical`
- [ ] `workspace.profile.tab.admin`
- [ ] query player-linked documents from `WorkspaceDocumentRepository`
- [ ] filter/group by category:
- [ ] `GENERAL`
- [ ] `REPORT`
- [ ] `MEDICAL`
- [ ] `ADMIN`
- [ ] `CONTRACT`
- [ ] merge admin + contract sections if intended by API
- [ ] generate download URLs through `StoragePort`
- [ ] return only permitted sections
- [ ] hidden sections should be `null` exactly as defined by API contract

### 3.2 Player Profile Design Checks
- [ ] service acts as Facade, not controller orchestration
- [ ] counts must match section contents
- [ ] profile can tolerate missing categories cleanly
- [ ] player identity failures from canonical service are handled intentionally
- [ ] avoid repeated category scans if query structure can be improved later

### 4. Implement OnlyOffice Config Endpoint
- [ ] create `OnlyOfficeConfigRequest`
- [ ] create `OnlyOfficeConfigResponse`
- [ ] create `OnlyOfficeConfigService`
- [ ] create `OnlyOfficeController`

### 4.1 OnlyOffice Config Rules
- [ ] load workspace document by `resourceId`
- [ ] reject documents without a current version
- [ ] compute access action:
- [ ] read -> `workspace.document.{category}.read`
- [ ] edit -> `workspace.document.{category}.edit`
- [ ] enforce through `PolicyClient`
- [ ] generate short-lived file URL with `StoragePort`
- [ ] derive file type and document type from MIME/content type
- [ ] build OnlyOffice key using document + version
- [ ] include callback URL
- [ ] sign config with JJWT
- [ ] return config + token together

### 4.2 OnlyOffice Security Checks
- [ ] JWT secret length/format is valid for signing algorithm
- [ ] edit mode is denied unless policy allows
- [ ] PDFs can be view-only
- [ ] unsupported file types are handled explicitly
- [ ] callback URL is consistent with workspace host/port expectations

### 5. Extend OPA Workspace Rules
- [ ] append player profile tab rules
- [ ] append document edit rules
- [ ] ensure:
- [ ] coaching staff can see documents/reports
- [ ] medical roles can see medical tab
- [ ] only admin can see admin tab
- [ ] general/report edit rules align with upload rules
- [ ] medical edit is stricter than medical upload if intended
- [ ] admin edit remains admin-only
- [ ] action strings exactly match backend code

### 6. Add Integration Tests
- [ ] create `PlayerProfileIntegrationTest`
- [ ] create `OnlyOfficeConfigTest`

### 6.1 Player Profile Test Coverage
- [ ] profile returns player identity
- [ ] documents section populated for linked player docs
- [ ] medical section works for medical docs
- [ ] tab counts are correct
- [ ] WireMock stubs:
- [ ] policy allow
- [ ] canonical player lookup
- [ ] storage provider can stay noop

### 6.2 OnlyOffice Config Test Coverage
- [ ] config endpoint returns `200`
- [ ] returned config contains token
- [ ] file URL is present
- [ ] mode handling works
- [ ] denied access path is covered if feasible
- [ ] JWT secret config is exercised in test properties

### 7. Verification
- [ ] run workspace tests
- [ ] verify `Migration003` appears in Mongock changelog
- [ ] boot OnlyOffice in compose
- [ ] request profile endpoint manually
- [ ] request OnlyOffice config endpoint manually
- [ ] verify generated callback URL and token structure

## Strict Acceptance Checklist
- [ ] OnlyOffice container boots locally
- [ ] workspace app reads OnlyOffice config from properties
- [ ] `Migration003AddPlayerProfileIndex` runs
- [ ] profile endpoint returns structured composite response
- [ ] role-gated sections are enforced by policy
- [ ] canonical player data is included
- [ ] medical/admin rules are represented in OPA
- [ ] OnlyOffice config endpoint returns signed config
- [ ] no direct file-byte proxying was added
- [ ] no duplicate data store for profiles was introduced

## Risks
- [ ] Sprint 1.3 depends heavily on Sprint 1.1 document correctness
- [ ] OnlyOffice container healthcheck/image behavior may differ locally
- [ ] MIME type handling for OnlyOffice can become brittle
- [ ] profile tab access can drift from OPA action names
- [ ] integration tests may still be impacted by local Docker/Testcontainers issues

## Risk Mitigations
- [ ] verify OPA action strings before writing tests
- [ ] keep OnlyOffice config generation pure and deterministic
- [ ] test file-type mapping separately if needed
- [ ] use WireMock for canonical and policy dependencies
- [ ] validate OnlyOffice runtime config with one manual smoke request early

## Suggested Commit Boundaries
- [ ] commit 1: OnlyOffice runtime/config/pom deps
- [ ] commit 2: Migration003
- [ ] commit 3: Player profile service/controller/response
- [ ] commit 4: OnlyOffice config service/controller/DTOs
- [ ] commit 5: OPA rules
- [ ] commit 6: integration tests
- [ ] commit 7: verification and cleanup

## Out of Scope
- [ ] OnlyOffice save callback persistence flow
- [ ] frontend player-profile tabs UI
- [ ] frontend editor embedding
- [ ] notification inbox
- [ ] search endpoint
- [ ] mobile/responsive UX
- [ ] advanced document collaboration beyond config generation

## Exit Criteria
Sprint 1.3 is complete only when:
- [ ] workspace profile aggregation works
- [ ] OnlyOffice config endpoint works
- [ ] OPA rules for profile/edit access are loaded
- [ ] migration runs cleanly
- [ ] integration tests pass or only fail for documented environment reasons
