# 08 — Backend Cleanup

## 1. Purpose
Clean backend risk areas that are not covered by Keycloak, service-flow, storage, or OnlyOffice plans: fallback IDs, host-run scripts, and generated/documentation cleanup boundaries.

## 2. Source Errors From erreurs.md
- Section 7: Backend Problems
- Section 10: Backend/docs cleanup list
- Section 11: Backend placeholder/fallback values
- Section 12: Native script mismatch

## 3. Classification
- Host-run scripts missing `-am`: Agent-fixable.
- Fallback UUID handling in no-auth mode: Mixed; code can improve naming/logging, real IDs require MANUAL-003.
- `AI_CONTEXT/` and docs cleanup: Needs human confirmation if deleting; otherwise safe to ignore.
- Maven version preservation: Agent constraint.

## 4. Files Allowed To Modify
- `run-gateway.sh`
- `run-governance.sh`
- `run-workspace.sh`
- `Makefile`
- `README.md`
- Backend service tests if script/doc changes require them
- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not change any `pom.xml` version from `0.1.0-SNAPSHOT`.
- Do not delete `AI_CONTEXT/` or `diagrams/` without explicit user confirmation.
- Do not change Mongo schema outside Mongock.

## 6. Current Problem Summary
Host-run scripts can fail on clean machines because they omit `-am`. Backend no-auth fallbacks use fake UUIDs and must remain clearly dev-only. Some documentation/agent folders are not runtime but should not be deleted without confirmation.

## 7. Target State
Host-run scripts build dependent SDK modules automatically. Dev-only fallbacks are documented and bounded. Non-runtime docs are classified but not deleted.

## 8. Step-by-Step Execution Plan
### Step 1: Add `-am` to host-run Maven scripts
- Objective: Let clean workstations run service modules with required SDK dependencies.
- Files to inspect: `run-gateway.sh`, `run-governance.sh`, `run-workspace.sh`, README commands
- Files to modify: `run-gateway.sh`, `run-governance.sh`, `run-workspace.sh`, README
- Exact change to make: Change `mvn "-Dspring-boot.run.arguments=--server.port=${PORT}" -pl "${MODULE}" spring-boot:run` to include `-am`, for example `mvn "-Dspring-boot.run.arguments=--server.port=${PORT}" -pl "${MODULE}" -am spring-boot:run`.
- Safety rule: Do not change ports or profiles in this step.
- Verification command: `Select-String -Path run-*.sh -Pattern "-am"`
- Expected result: All three run scripts include `-am`.
- What to do if verification fails: Edit the missing script and rerun.

### Step 2: Bound fallback UUID behavior
- Objective: Make no-auth fallback IDs clearly dev-only and avoid accidental production reliance.
- Files to inspect: `DocumentService.java`, `EventService.java`, `OnlyOfficeConfigService.java`, tests
- Files to modify: Service classes or README only if needed
- Exact change to make: Rename constants/comments to make fallback values explicit as no-auth dev fallbacks. Optionally centralize them behind config properties such as `fos.dev.fallback-club-id`, but do not require real IDs without MANUAL-003.
- Safety rule: Do not remove no-auth fallback if tests/dev depend on it.
- Verification command: `mvn -pl fos-workspace-service -am -DskipTests compile`
- Expected result: Workspace compiles.
- What to do if verification fails: Revert constant/config change.

### Step 3: Verify backend architecture constraints
- Objective: Ensure cleanup did not violate architecture.
- Files to inspect: Backend source
- Files to modify: None unless violation found
- Exact change to make: Run searches for raw Mongo clients, direct governance imports in workspace, direct storage clients in workspace, and Kafka consumers not extending `AbstractFosConsumer`.
- Safety rule: Stop on violations and create separate targeted fix.
- Verification command: `rg -n "MongoClient|com\\.fos\\.governance|MinioClient|@KafkaListener" fos-workspace-service\\src\\main\\java`
- Expected result: No forbidden domain/application usage; `@KafkaListener` only in consumer that extends `AbstractFosConsumer`.
- What to do if verification fails: Do not refactor broadly; document exact violation.

### Step 4: Classify non-runtime folders without deleting
- Objective: Keep cleanup list accurate.
- Files to inspect: `AI_CONTEXT/`, `diagrams/`, README
- Files to modify: README or a cleanup note only
- Exact change to make: Document `AI_CONTEXT/` as agent-history docs and `diagrams/` as documentation. Do not delete.
- Safety rule: No deletion.
- Verification command: `git status --short`
- Expected result: Only intended docs/script/source changes appear.
- What to do if verification fails: Revert unintended file changes.

## 9. Verification Commands
- `Select-String -Path run-*.sh -Pattern "-am"`
- `mvn -pl fos-workspace-service -am -DskipTests compile`
- `mvn compile -DskipTests`
- `rg -n "MongoClient|com\\.fos\\.governance|MinioClient|@KafkaListener" fos-workspace-service\\src\\main\\java`
- `git status --short`

## 10. Acceptance Criteria
- [ ] Host-run scripts include `-am`.
- [ ] Fallback UUIDs are clearly dev/no-auth only or configurable.
- [ ] Maven versions remain `0.1.0-SNAPSHOT`.
- [ ] Architecture constraints remain satisfied.
- [ ] Non-runtime docs are classified but not deleted.

## 11. Rollback Plan
Restore scripts, README, and backend service files from Git. Do not alter local databases, Kafka topics, or generated build folders.

## 12. Notes For The Execution Agent
Do not duplicate work from plans 03, 04, 05, or 06. This is a cleanup and safety-boundary plan.
