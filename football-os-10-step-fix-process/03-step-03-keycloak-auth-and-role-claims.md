# Step 03 — Keycloak Auth And Role Claims

## 1. Purpose
Make local Keycloak reproducible and align frontend, backend, and policy role claims so secured local workflows can authenticate and authorize correctly.

## 2. Errors Covered
- Full compose starts Keycloak without a reproducible `fos` realm/client import.
- Frontend Keycloak settings may not align with the imported realm/client.
- Backend role extraction reads only top-level `roles` and misses standard Keycloak claims.
- `fos_club_id` claim strategy is not clearly documented or mapped.
- Browser sessions must be refreshed after mapper or token parsing changes.

## 3. Files To Inspect
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `README.md`
- `fos-workspace-frontend/src/environments/environment.ts`
- `fos-workspace-frontend/src/environments/environment.development.ts`
- `fos-workspace-frontend/src/app/core/auth/auth.service.ts`
- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java`
- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java`
- `fos-sdk/sdk-security/src/test/java/com/fos/sdk/security/`
- Existing `keycloak/` folder if present

## 4. Files Allowed To Modify
- `keycloak/fos-realm.local.json`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `README.md`
- `fos-workspace-frontend/src/environments/environment.ts`
- `fos-workspace-frontend/src/environments/environment.development.ts`
- `fos-workspace-frontend/src/app/core/auth/auth.service.ts`
- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java`
- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java`
- `fos-sdk/sdk-security/src/test/java/com/fos/sdk/security/`

## 5. Files Forbidden To Modify
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
- Maven versions in any `pom.xml`
- Production Keycloak users, passwords, or secrets.

## 6. Automatic Fixes To Perform
1. Add a local Keycloak realm import.
   - Objective: Make realm/client setup reproducible.
   - Exact implementation instruction: Create or update `keycloak/fos-realm.local.json` for realm `fos` with public client `fos-workspace-frontend`, Authorization Code with PKCE, redirect URI `http://localhost:4200/*`, web origin `http://localhost:4200`, and local roles `ROLE_HEAD_COACH`, `ROLE_CLUB_ADMIN`, `ROLE_MEDICAL_STAFF`, and `ROLE_ANALYST`. Mount it read-only to `/opt/keycloak/data/import/fos-realm.local.json` and run Keycloak with `start-dev --import-realm`.
   - Safety rule: Do not include real production users, passwords, or secrets.
   - Verification command: `docker compose config; Select-String -Path keycloak/fos-realm.local.json -Pattern "fos-workspace-frontend|ROLE_HEAD_COACH|fos"`
   - Expected result: Compose includes the import mount/command and realm JSON includes the realm, client, and roles.
   - Stop condition if it fails: Stop if existing Keycloak data prevents import; record `MANUAL-002` instead of deleting data.

2. Align frontend Keycloak settings.
   - Objective: Ensure Angular uses the same realm and client as the local import.
   - Exact implementation instruction: Confirm frontend environment/auth config uses realm `fos`, client `fos-workspace-frontend`, and Keycloak URL `http://localhost:8180` for local development. Do not add secrets for this public client.
   - Safety rule: Do not hardcode production auth endpoints.
   - Verification command: `Select-String -Path fos-workspace-frontend/src/environments/environment*.ts -Pattern "fos-workspace-frontend|localhost:8180|realm|fos"`
   - Expected result: Frontend local auth config matches the imported realm/client.
   - Stop condition if it fails: Stop if frontend auth settings are supplied only by a runtime config system not covered by this step.

3. Define the `fos_club_id` claim strategy.
   - Objective: Make tenant/club authorization data explicit.
   - Exact implementation instruction: Add local realm mapper configuration or README instructions for `fos_club_id`. Use fake/local UUID placeholders only if the realm import includes local demo users; otherwise document that real IDs require `MANUAL-003`.
   - Safety rule: Do not invent production club, team, or actor IDs.
   - Verification command: `Select-String -Path keycloak/fos-realm.local.json,README.md -Pattern "fos_club_id"`
   - Expected result: The claim strategy is implemented locally or documented with the manual blocker.
   - Stop condition if it fails: Stop and record `MANUAL-003` if real IDs are required.

4. Support standard Keycloak role claim locations.
   - Objective: Make backend authorization robust for standard Keycloak tokens.
   - Exact implementation instruction: Preserve top-level `roles` support and add extraction from `realm_access.roles` and `resource_access.<client>.roles`, including `resource_access.fos-workspace-frontend.roles`. Preserve `ROLE_` prefixes because policies use role names such as `ROLE_HEAD_COACH`.
   - Safety rule: Do not remove existing top-level role behavior.
   - Verification command: `mvn -pl fos-sdk/sdk-security -am test`
   - Expected result: SDK security tests pass for all three claim locations.
   - Stop condition if it fails: If tests cannot run due to environment, run `mvn -pl fos-sdk/sdk-security -am -DskipTests compile`; if compile fails, revert the role extraction change and stop.

5. Document browser session refresh.
   - Objective: Prevent stale tokens after mapper or parser changes.
   - Exact implementation instruction: Add README notes instructing the user to log out, clear site data for `localhost:4200` and `localhost:8180` if needed, and log in again after Keycloak mapper/security changes.
   - Safety rule: Do not attempt to clear browser storage automatically.
   - Verification command: `Select-String -Path README.md -Pattern "browser|session|token|localhost:8180"`
   - Expected result: Browser refresh instructions are documented.
   - Stop condition if it fails: Record `MANUAL-006`.

## 7. Manual-Only Blockers
- `MANUAL-002`: User must verify/import Keycloak realm through Admin UI if auto-import does not apply.
- `MANUAL-003`: User must provide real club UUID, team UUID, actor UUID, and role names.
- `MANUAL-006`: User must refresh browser session after Keycloak or token changes.

## 8. Verification Commands
- `docker compose config`
- `Select-String -Path keycloak/fos-realm.local.json -Pattern "fos-workspace-frontend|ROLE_HEAD_COACH|fos_club_id"`
- `mvn -pl fos-sdk/sdk-security -am test`
- `mvn compile -DskipTests`
- `cd fos-workspace-frontend; npm run build`
- `curl http://localhost:8180/realms/fos/.well-known/openid-configuration`

## 9. Acceptance Criteria
- [ ] Local Keycloak realm import exists and is mounted in compose.
- [ ] Frontend realm/client settings match the import.
- [ ] Backend extracts roles from top-level, realm, and client role claims.
- [ ] `fos_club_id` is implemented for local import or documented as manual data.
- [ ] Browser session refresh is documented.

## 10. Documentation To Update
Update `README.md` with Keycloak import behavior, realm/client settings, local roles, `fos_club_id` claim strategy, Admin UI fallback, and browser refresh instructions.

## 11. Rollback Plan
Revert Keycloak import files, compose changes, frontend auth config changes, SDK security changes, tests, and README edits from this step. Do not delete Keycloak database volumes or users. If import data was already loaded, leave local Keycloak state for the user to manage manually.
