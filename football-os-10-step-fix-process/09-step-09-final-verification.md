# Step 09 — Final Verification

## 1. Purpose
Run final verification after Steps 01 through 08 have completed or stopped on documented manual blockers. This step must not modify application files. If a check fails, identify the owning step and stop.

## 2. Errors Covered
- Git scope may include unintended files.
- Backend compile may fail after cross-module changes.
- Frontend build may fail after runtime cleanup.
- Full, infra, and no-auth compose configs may still be invalid.
- Docker health checks require running services.
- Keycloak discovery must prove realm availability.
- OnlyOffice API script must be reachable.
- Real document smoke tests require existing local data.

## 3. Files To Inspect
- Git status output
- Maven build output
- Angular build output
- Docker compose config output
- Docker service health output
- Keycloak discovery response
- OnlyOffice `api.js` response headers
- Browser/API output for real document smoke tests when data exists
- `README.md`
- Final report from Step 10 if already drafted

## 4. Files Allowed To Modify
- None.

## 5. Files Forbidden To Modify
- All application files.
- `.env`
- `.env.dev`
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `.git/`
- Docker volumes
- Database data
- MinIO buckets or objects.

## 6. Automatic Fixes To Perform
1. Verify Git scope.
   - Objective: Confirm only intended files changed.
   - Exact implementation instruction: Run `git status --short` and compare changed paths to Steps 01 through 08. No forbidden folders may appear.
   - Safety rule: Do not revert user changes without explicit permission.
   - Verification command: `git status --short`
   - Expected result: Changed files match the executed steps and no forbidden folders are listed.
   - Stop condition if it fails: Stop and report unexpected paths.

2. Run backend compile.
   - Objective: Confirm Java modules compile together.
   - Exact implementation instruction: Run full Maven compile with tests skipped.
   - Safety rule: Do not start services or run migrations in this task.
   - Verification command: `mvn compile -DskipTests`
   - Expected result: Reactor build succeeds.
   - Stop condition if it fails: Identify owning step by module: SDK security Step 03, gateway Step 04, governance/OPA Step 02 or 04, workspace storage/OnlyOffice/notification Step 04/05/06/08.

3. Run frontend build.
   - Objective: Confirm Angular production build succeeds.
   - Exact implementation instruction: Run `npm run build` from `fos-workspace-frontend`.
   - Safety rule: Do not commit or edit `dist/`.
   - Verification command: `cd fos-workspace-frontend; npm run build`
   - Expected result: Build succeeds; warnings are documented if accepted.
   - Stop condition if it fails: Return ownership to Step 07 and stop.

4. Validate compose configurations.
   - Objective: Confirm full, infra, and no-auth compose configs resolve.
   - Exact implementation instruction: Run config-only commands for full compose, infra compose with `.env.dev`, and full compose plus no-auth overlay.
   - Safety rule: Config only; do not start containers in this task.
   - Verification command: `docker compose config; docker compose --env-file .env.dev -f docker-compose.infra.yml config; docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
   - Expected result: All configs resolve without missing variable errors.
   - Stop condition if it fails: Return ownership to Step 01 or Step 02.

5. Run Docker health checks when Docker is available.
   - Objective: Confirm running local services are healthy.
   - Exact implementation instruction: If Docker Desktop is running, inspect `docker compose ps` and query gateway, governance, and workspace health endpoints. Use the user's selected stack mode.
   - Safety rule: Do not use `docker compose down -v` or reset volumes.
   - Verification command: `docker compose ps; curl http://localhost:8080/actuator/health; curl http://localhost:8081/actuator/health; curl http://localhost:8082/actuator/health`
   - Expected result: Services are healthy or return expected health JSON.
   - Stop condition if it fails: If Docker is unavailable, record `MANUAL-001`; otherwise capture failing service/status and return to owning step.

6. Verify Keycloak discovery.
   - Objective: Confirm realm `fos` exists and is reachable.
   - Exact implementation instruction: Query the OpenID discovery endpoint after Keycloak is running.
   - Safety rule: Do not delete Keycloak data to force import.
   - Verification command: `curl http://localhost:8180/realms/fos/.well-known/openid-configuration`
   - Expected result: JSON discovery document for realm `fos`.
   - Stop condition if it fails: Record `MANUAL-002` unless compose config is the obvious owner.

7. Verify OnlyOffice API script.
   - Objective: Confirm Document Server frontend script is reachable.
   - Exact implementation instruction: Query `api.js` after OnlyOffice is running.
   - Safety rule: Do not change OnlyOffice config during verification.
   - Verification command: `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js`
   - Expected result: Successful response, preferably HTTP `200`.
   - Stop condition if it fails: Return ownership to Step 06 or record Docker runtime blocker.

8. Run real document smoke only when data exists.
   - Objective: Verify gateway, auth/no-auth, MinIO, workspace, and OnlyOffice together.
   - Exact implementation instruction: With real team/document/actor data from the user, call OnlyOffice config through gateway, open the document in the frontend, save it, and verify the save callback creates a new version or expected save response.
   - Safety rule: Use test documents only and do not invent UUIDs.
   - Verification command: Browser/API smoke through gateway using user-provided IDs.
   - Expected result: Calendar/document APIs return expected data and OnlyOffice opens/saves the test document.
   - Stop condition if it fails: Record endpoint, status, response body, browser console/network error, and owning step.

## 7. Manual-Only Blockers
- `MANUAL-001`: Docker Desktop must be running for service health checks.
- `MANUAL-002`: Keycloak Admin UI verification/import may be required.
- `MANUAL-006`: Browser session refresh may be required after auth changes.
- `MANUAL-008`: Real MinIO object must exist for document editing smoke.
- `MANUAL-010`: Real team/document/actor UUIDs are required for final smoke tests.

## 8. Verification Commands
- `git status --short`
- `mvn compile -DskipTests`
- `cd fos-workspace-frontend; npm run build`
- `docker compose config`
- `docker compose --env-file .env.dev -f docker-compose.infra.yml config`
- `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- `docker compose ps`
- `curl http://localhost:8080/actuator/health`
- `curl http://localhost:8081/actuator/health`
- `curl http://localhost:8082/actuator/health`
- `curl http://localhost:8180/realms/fos/.well-known/openid-configuration`
- `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js`

## 9. Acceptance Criteria
- [ ] No forbidden folders were edited.
- [ ] Backend compile succeeds.
- [ ] Frontend build succeeds.
- [ ] Full compose config succeeds.
- [ ] Infra compose config succeeds or is blocked only by missing local `.env.dev`.
- [ ] No-auth compose config succeeds.
- [ ] Docker health checks pass when Docker is running.
- [ ] Keycloak discovery returns realm `fos` JSON when auth stack is running.
- [ ] OnlyOffice `api.js` is reachable when Document Server is running.
- [ ] Real document smoke passes or is blocked only by documented manual data prerequisites.

## 10. Documentation To Update
Do not update documentation in this step. Pass all command results, failures, and blockers to Step 10 for the final handoff.

## 11. Rollback Plan
This step modifies no application files. If verification reveals a regression, rollback through the specific owning step that introduced it. Do not revert unrelated user changes.
