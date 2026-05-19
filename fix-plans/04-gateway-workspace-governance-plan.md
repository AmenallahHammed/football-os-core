# 04 — Gateway Workspace Governance

## 1. Purpose
Stabilize the gateway/workspace/governance service-to-service flow, especially policy calls, no-auth behavior, and actor propagation.

## 2. Source Errors From erreurs.md
- Section 7: Backend Problems
- Section 8: Docker no-auth overlay issue
- Section 12: No-auth mismatch, runtime data mismatch, service flow mismatches
- Related ERR-004 role behavior from `03-keycloak-auth-security-plan.md`

## 3. Classification
- Fix no-auth overlay consistency: Agent-fixable with `02-docker-and-infrastructure-plan.md`.
- Ensure policy calls go through `PolicyClient`: Agent-fixable verification.
- Fix document upload notification actorRef: Agent-fixable.
- Confirm real actor/club IDs: Manual-only; see MANUAL-003.

## 4. Files Allowed To Modify
- `docker-compose.noauth.yml`
- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java`
- Related tests under `fos-workspace-service/src/test/java/`
- `README.md`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not replace `PolicyClient` with direct OPA calls from workspace.
- Do not make workspace depend on governance internals.
- Do not replace `AbstractFosConsumer`.

## 6. Current Problem Summary
Workspace policy/canonical calls depend on governance. In no-auth mode, gateway/workspace can be unauthenticated while governance may still require JWT. Document upload notifications use the owner ref as actorRef, which can point to club instead of uploader.

## 7. Target State
Gateway routes all frontend API traffic. Workspace calls governance through SDK clients. No-auth mode behaves consistently. Kafka notifications use correct actor semantics or explicitly documented recipient semantics.

## 8. Step-by-Step Execution Plan
### Step 1: Verify gateway route coverage
- Objective: Confirm frontend APIs route through gateway.
- Files to inspect: `fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java`, frontend API services
- Files to modify: Gateway route config only if a route is missing
- Exact change to make: Ensure `/api/v1/documents/**`, `/events/**`, `/profiles/**`, `/notifications/**`, `/search/**`, and `/onlyoffice/**` route to workspace; governance routes include actors, players, teams, policy, signals, identity.
- Safety rule: Do not route frontend directly to port 8082.
- Verification command: `mvn -pl fos-gateway -am -DskipTests compile`
- Expected result: Gateway compiles.
- What to do if verification fails: Revert route edit and inspect syntax.

### Step 2: Align no-auth service flow
- Objective: Prevent workspace policy requests from failing against secured governance in no-auth mode.
- Files to inspect: `docker-compose.noauth.yml`, workspace/governance security configs
- Files to modify: `docker-compose.noauth.yml`, `README.md`
- Exact change to make: Apply the decision from `02-docker-and-infrastructure-plan.md`: either disable governance security in the no-auth overlay or document that no-auth only bypasses gateway/workspace and requires mock policy routing.
- Safety rule: Do not disable security in default `docker-compose.yml`.
- Verification command: `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- Expected result: Config shows intended security values.
- What to do if verification fails: Revert and keep limitation documented.

### Step 3: Fix upload notification actorRef semantics
- Objective: Avoid assigning uploader notifications to the club UUID.
- Files to inspect: `DocumentService.java`, `WorkspaceKafkaConsumer.java`, notification tests
- Files to modify: `DocumentService.java`, `WorkspaceKafkaConsumer.java`, tests if needed
- Exact change to make: Emit a payload field such as `uploaderActorId` and/or set `actorRef` to the actual actor UUID in a parseable format. Update `WorkspaceKafkaConsumer` to read explicit `uploaderActorId` first, then legacy `actorRef`.
- Safety rule: Keep consumer extending `AbstractFosConsumer`; maintain backward compatibility for old messages.
- Verification command: `mvn -pl fos-workspace-service -am -DskipTests compile`
- Expected result: Workspace compiles.
- What to do if verification fails: Revert consumer changes and add a failing unit test first.

### Step 4: Verify architecture constraints
- Objective: Ensure no direct governance internals or raw OPA calls were introduced.
- Files to inspect: Workspace service source
- Files to modify: None unless violation found
- Exact change to make: Use `rg` to confirm workspace imports `com.fos.sdk.policy.PolicyClient` and does not import `com.fos.governance`.
- Safety rule: Verification only unless a violation is found.
- Verification command: `rg -n "com\\.fos\\.governance|OpaClient|MongoClient" fos-workspace-service\\src\\main\\java`
- Expected result: No forbidden imports/usages in workspace domain/application layers.
- What to do if verification fails: Stop and create a separate remediation plan.

## 9. Verification Commands
- `mvn -pl fos-gateway -am -DskipTests compile`
- `mvn -pl fos-workspace-service -am -DskipTests compile`
- `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- `rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend\\src`
- `rg -n "com\\.fos\\.governance|OpaClient|MongoClient" fos-workspace-service\\src\\main\\java`

## 10. Acceptance Criteria
- [ ] Gateway route coverage matches frontend API calls.
- [ ] No frontend source calls workspace `8082` directly.
- [ ] No-auth overlay behavior is clear and consistent.
- [ ] Upload notifications no longer confuse club owner ID with uploader actor ID.
- [ ] Architecture constraints remain intact.

## 11. Rollback Plan
Restore route, compose overlay, `DocumentService`, `WorkspaceKafkaConsumer`, and tests from Git. Do not delete Kafka topics or database data.

## 12. Notes For The Execution Agent
Do not solve Keycloak claim mapping here; that belongs to plan 03. Do not change OPA policy rules here; that belongs to plan 02 or a future policy-specific plan.
