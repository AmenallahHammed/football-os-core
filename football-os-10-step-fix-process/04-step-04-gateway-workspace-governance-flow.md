# Step 04 — Gateway Workspace Governance Flow

## 1. Purpose
Stabilize the service flow between frontend, gateway, workspace, and governance. This step ensures all frontend API calls go through gateway, workspace uses SDK clients for governance/policy work, no-auth mode is consistent, and document upload notifications use correct actor semantics.

## 2. Errors Covered
- Frontend may call workspace directly on port `8082`.
- Gateway may lack routes for active workspace or governance API families.
- Workspace-to-governance calls must go through SDK clients, not governance internals.
- Workspace authorization must use `PolicyClient`, not direct OPA.
- No-auth mode can leave governance secured while gateway/workspace are unauthenticated.
- Upload notification actor semantics may confuse owner/club ID with uploader actor ID.

## 3. Files To Inspect
- `fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java`
- `docker-compose.noauth.yml`
- `README.md`
- `fos-workspace-frontend/src/app/`
- `fos-workspace-frontend/src/environments/`
- `fos-workspace-service/src/main/java/`
- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java`
- `fos-workspace-service/src/test/java/`
- `fos-sdk/`

## 4. Files Allowed To Modify
- `fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java`
- `docker-compose.noauth.yml`
- `README.md`
- `fos-workspace-frontend/src/app/`
- `fos-workspace-frontend/src/environments/`
- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java`
- Related tests under `fos-workspace-service/src/test/java/`

## 5. Files Forbidden To Modify
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `.git/`
- Governance internals from workspace code.
- Direct OPA clients in workspace.
- Kafka consumer base class replacement.

## 6. Automatic Fixes To Perform
1. Enforce gateway-only frontend API calls.
   - Objective: Remove direct workspace `8082` calls from Angular runtime code.
   - Exact implementation instruction: Search frontend source for `localhost:8082` and `http://localhost:8082`. Replace runtime API usage with the configured gateway base URL, defaulting locally to `http://localhost:8080`.
   - Safety rule: Do not hardcode service ports inside Angular feature services.
   - Verification command: `rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend/src`
   - Expected result: No runtime Angular source calls workspace directly; docs or tests may remain only if clearly non-runtime.
   - Stop condition if it fails: Stop if the reference is generated or test-only and cannot be safely classified.

2. Verify and fix gateway route coverage.
   - Objective: Ensure gateway routes all active frontend API families.
   - Exact implementation instruction: Ensure workspace route coverage includes `/api/v1/documents/**`, `/api/v1/events/**`, `/api/v1/profiles/**`, `/api/v1/notifications/**`, `/api/v1/search/**`, and `/api/v1/onlyoffice/**`. Ensure governance route coverage includes `/api/v1/actors/**`, `/api/v1/players/**`, `/api/v1/teams/**`, `/api/v1/policy/**`, `/api/v1/signals/**`, and `/api/v1/identity/**`, adjusted only if controller paths prove different.
   - Safety rule: Do not route frontend directly to workspace.
   - Verification command: `mvn -pl fos-gateway -am -DskipTests compile`
   - Expected result: Gateway compiles and route families are present.
   - Stop condition if it fails: Stop if route conventions differ from controllers and cannot be confirmed.

3. Align no-auth flow with Step 02.
   - Objective: Prevent workspace policy calls from failing against secured governance in no-auth mode.
   - Exact implementation instruction: Confirm `docker-compose.noauth.yml` sets `FOS_SECURITY_ENABLED=false` consistently for gateway, workspace, and governance, or document a confirmed mock-policy route if the overlay intentionally keeps governance protected.
   - Safety rule: Do not disable security in default full compose.
   - Verification command: `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
   - Expected result: No-auth merged config is internally consistent.
   - Stop condition if it fails: Return to Step 02 and stop this step.

4. Fix upload notification actor semantics.
   - Objective: Avoid using a club/owner UUID as the uploader actor UUID.
   - Exact implementation instruction: When emitting document upload signals, include an explicit uploader actor field such as `uploaderActorId`. Update `WorkspaceKafkaConsumer` to read `uploaderActorId` first and fall back to legacy `actorRef` only when absent.
   - Safety rule: `WorkspaceKafkaConsumer` must continue to extend `AbstractFosConsumer` and remain backward compatible with old messages.
   - Verification command: `mvn -pl fos-workspace-service -am -DskipTests compile`
   - Expected result: Workspace compiles and actor semantics are explicit.
   - Stop condition if it fails: Stop if payload shape cannot support the new metadata without breaking existing consumers.

5. Verify architecture boundaries.
   - Objective: Ensure workspace remains decoupled from governance and direct infrastructure clients.
   - Exact implementation instruction: Search workspace source for forbidden direct imports/usages. If a violation is found, replace it with SDK client usage only if the fix is local and obvious; otherwise stop with file and line details.
   - Safety rule: Workspace must use `PolicyClient` for permissions and must not call OPA directly.
   - Verification command: `rg -n "com\\.fos\\.governance|OpaClient|MongoClient|MinioClient|S3Client" fos-workspace-service/src/main/java`
   - Expected result: No forbidden usage in workspace domain/application code.
   - Stop condition if it fails: Stop and report exact violations instead of broad refactoring.

## 7. Manual-Only Blockers
- `MANUAL-003`: User must provide real actor/club/team UUIDs if notification or policy behavior requires real local data.

## 8. Verification Commands
- `rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend/src`
- `mvn -pl fos-gateway -am -DskipTests compile`
- `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- `mvn -pl fos-workspace-service -am -DskipTests compile`
- `rg -n "com\\.fos\\.governance|OpaClient|MongoClient|MinioClient|S3Client" fos-workspace-service/src/main/java`
- `rg -n "@KafkaListener|extends AbstractFosConsumer" fos-workspace-service/src/main/java fos-governance-service/src/main/java`

## 9. Acceptance Criteria
- [ ] Frontend runtime code calls gateway on port `8080`, not workspace on `8082`.
- [ ] Gateway has route coverage for active workspace and governance APIs.
- [ ] No-auth flow is consistent across gateway, workspace, and governance.
- [ ] Workspace uses `PolicyClient`, not direct OPA.
- [ ] Workspace does not import governance internals.
- [ ] Upload notification actor semantics distinguish uploader from owner/club.

## 10. Documentation To Update
Update `README.md` with gateway-only frontend API rule, no-auth flow behavior, policy client boundary, and any notification actor compatibility notes.

## 11. Rollback Plan
Revert gateway route changes, no-auth overlay changes, frontend API URL changes, workspace notification changes, and related tests from this step. Do not delete Kafka topics, database rows, or generated files.
