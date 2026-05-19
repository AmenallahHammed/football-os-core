# 09 — Verification

## 1. Purpose
Provide the final verification sequence after plans 01-08 are executed.

## 2. Source Errors From erreurs.md
- Section 13: Verification Results
- Section 14: Recommended Fix Order
- All ERR items and all mismatches after repair plans

## 3. Classification
- Static/build/config verification: Agent-fixable.
- Docker engine availability: Manual-only if not running; see MANUAL-001.
- Authenticated browser login, real document editing, and real data checks: Mixed; see MANUAL-002, MANUAL-006, MANUAL-008, MANUAL-010.

## 4. Files Allowed To Modify
- `fix-plans/09-verification-results.md` if the execution agent wants to record final outputs
- No application files should be modified in this plan unless verification reveals a blocker and the user approves returning to a category plan.

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not edit source files during final verification.
- Do not run destructive Docker or database commands.

## 6. Current Problem Summary
Initial audit showed backend compile and Angular build can pass, but Docker services were not running and full compose had known config gaps. Final verification must prove those gaps are closed.

## 7. Target State
All build/config checks pass, local compose resolves and can start, auth endpoints exist, frontend builds, and documented smoke tests either pass or are blocked only by explicit manual prerequisites.

## 8. Step-by-Step Execution Plan
### Step 1: Verify Git scope
- Objective: Confirm only intended files changed.
- Files to inspect: Git status
- Files to modify: None
- Exact change to make: Run status and review changed paths.
- Safety rule: Do not revert user changes in `report/` folders.
- Verification command: `git status --short`
- Expected result: Changes match executed plans; no forbidden folders edited.
- What to do if verification fails: Stop and ask user before touching unexpected changes.

### Step 2: Run backend compile
- Objective: Confirm Java modules compile.
- Files to inspect: Maven output
- Files to modify: None
- Exact change to make: Run full compile with skipped tests.
- Safety rule: Do not run migrations or start apps in this step.
- Verification command: `mvn compile -DskipTests`
- Expected result: Reactor build success.
- What to do if verification fails: Return to the category plan owning the failing module.

### Step 3: Run frontend build
- Objective: Confirm Angular app builds.
- Files to inspect: Angular build output
- Files to modify: None
- Exact change to make: Run production build.
- Safety rule: Do not commit generated `dist/`.
- Verification command: `npm run build` from `fos-workspace-frontend`
- Expected result: Build succeeds; warnings are either resolved or documented.
- What to do if verification fails: Return to plan 07.

### Step 4: Validate compose configurations
- Objective: Confirm Docker configs resolve.
- Files to inspect: Compose output
- Files to modify: None
- Exact change to make: Run full, infra, and no-auth config commands.
- Safety rule: Config commands only; do not start containers yet.
- Verification command: `docker compose config; docker compose --env-file .env.dev -f docker-compose.infra.yml config; docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- Expected result: All configs succeed without missing variable errors.
- What to do if verification fails: Return to plans 01 or 02.

### Step 5: Optional local service health smoke
- Objective: Verify running stack if Docker is available.
- Files to inspect: Container status and health endpoints
- Files to modify: None
- Exact change to make: With user approval if needed, start the selected stack and query health endpoints.
- Safety rule: Do not use `down -v`; do not reset databases.
- Verification command: `docker compose ps`, then health checks such as `curl http://localhost:8080/actuator/health`
- Expected result: Required services are healthy or startup blockers are recorded.
- What to do if verification fails: Capture failing service logs and return to owning plan.

### Step 6: Optional authenticated and document smoke
- Objective: Verify Keycloak, gateway, MinIO, and OnlyOffice together.
- Files to inspect: Browser/network/API responses
- Files to modify: None
- Exact change to make: After MANUAL-002, MANUAL-006, MANUAL-008, and MANUAL-010, test login, calendar list, document config, script load, and save callback.
- Safety rule: Use test documents only.
- Verification command: `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js` plus browser/API checks.
- Expected result: Login works, protected APIs accept token, OnlyOffice opens test document.
- What to do if verification fails: Record exact endpoint/status/body and return to plans 03, 05, or 06.

## 9. Verification Commands
- `git status --short`
- `mvn compile -DskipTests`
- `npm run build`
- `docker compose config`
- `docker compose --env-file .env.dev -f docker-compose.infra.yml config`
- `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- `docker compose ps`
- `curl http://localhost:8080/actuator/health`
- `curl http://localhost:8081/actuator/health`
- `curl http://localhost:8082/actuator/health`
- `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js`

## 10. Acceptance Criteria
- [ ] No forbidden folders were edited.
- [ ] Backend compile succeeds.
- [ ] Angular build succeeds.
- [ ] Full compose config succeeds.
- [ ] Infra compose config succeeds.
- [ ] No-auth compose config succeeds.
- [ ] Docker service health checks pass when Docker is running.
- [ ] Keycloak realm discovery works when auth stack is running.
- [ ] OnlyOffice `api.js` is reachable.
- [ ] Real document editing smoke either passes or is blocked only by documented manual data prerequisites.

## 11. Rollback Plan
This plan should not modify application files. If verification reveals a regression, rollback through the specific category plan that introduced it.

## 12. Notes For The Execution Agent
Verification is not a place to improvise fixes. If a check fails, identify the owning plan and stop or request permission to execute that plan.
