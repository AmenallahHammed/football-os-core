# 03 — Keycloak Auth Security

## 1. Purpose
Create a reproducible local Keycloak setup and align backend/frontend token claim handling so secured local compose can authenticate and authorize users.

## 2. Source Errors From erreurs.md
- ERR-001: Full Compose has Keycloak but no realm/client import
- ERR-004: Backend role extraction does not match standard Keycloak role claims
- Section 6: Frontend auth/environment issues
- Section 7: Backend security role extraction issues
- Section 12: Keycloak realm and role mismatches

## 3. Classification
- Create Keycloak realm import file: Agent-fixable.
- Mount/import realm in compose: Agent-fixable.
- Backend role extraction for standard Keycloak claims: Agent-fixable.
- Confirm real club/team/actor IDs and role names: Manual-only; see MANUAL-003.
- Admin UI import/verification if auto-import fails: Manual-only; see MANUAL-002.
- Browser logout/session refresh: Manual-only; see MANUAL-006.

## 4. Files Allowed To Modify
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `README.md`
- A new Keycloak config folder such as `keycloak/`
- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java`
- `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java`
- `fos-sdk/sdk-security/src/test/java/com/fos/sdk/security/FosSecurityContextTest.java`
- `fos-workspace-frontend/src/app/core/auth/auth.service.ts`
- Frontend auth tests under `fos-workspace-frontend/src/app/core/auth/`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not hardcode real user secrets.
- Do not change Maven version `0.1.0-SNAPSHOT`.

## 6. Current Problem Summary
Keycloak starts empty, while apps expect realm `fos` and client `fos-workspace-frontend`. Backend security reads only top-level `roles`, so standard Keycloak role claims may not authorize correctly.

## 7. Target State
Local compose can import a realm with a public Angular client and documented test roles/claim mappers. Backend can read top-level, realm, and client roles consistently. Frontend and backend agree on club/role claim names.

## 8. Step-by-Step Execution Plan
### Step 1: Add local realm import
- Objective: Make Keycloak reproducible.
- Files to inspect: `docker-compose.yml`, `docker-compose.infra.yml`, frontend environment files
- Files to modify: `keycloak/fos-realm.local.json`, `docker-compose.yml`, `docker-compose.infra.yml`, `README.md`
- Exact change to make: Create a realm import for realm `fos` with public client `fos-workspace-frontend`, Authorization Code with PKCE, redirect URI `http://localhost:4200/*`, web origin `http://localhost:4200`, and roles used by policies such as `ROLE_HEAD_COACH`, `ROLE_CLUB_ADMIN`, `ROLE_MEDICAL_STAFF`, `ROLE_ANALYST`. Mount it to `/opt/keycloak/data/import/fos-realm.local.json:ro` and change command to `start-dev --import-realm`.
- Safety rule: Do not include real passwords or real users unless explicitly approved.
- Verification command: `docker compose config`
- Expected result: Keycloak service shows import volume and import command.
- What to do if verification fails: Fix YAML paths and rerun.

### Step 2: Add role and club claim mapper strategy
- Objective: Ensure tokens contain claims backend can use.
- Files to inspect: planned realm JSON, `FosSecurityContext.java`, `auth.service.ts`
- Files to modify: `keycloak/fos-realm.local.json`, `README.md`
- Exact change to make: Add mapper/documentation for `roles` top-level claim and `fos_club_id` claim for local users if using import. If not creating users, document manual assignment in MANUAL-003.
- Safety rule: Use fake/local UUID placeholders only in import unless user provides real IDs.
- Verification command: `Select-String -Path keycloak\\fos-realm.local.json -Pattern "roles|fos_club_id|fos-workspace-frontend"`
- Expected result: Import file documents or defines required claims.
- What to do if verification fails: Add missing mapper entries or document manual requirement.

### Step 3: Update backend role extraction
- Objective: Make backend robust to standard Keycloak tokens.
- Files to inspect: `FosSecurityContext.java`, `FosJwtConverter.java`, frontend `auth.service.ts`, existing tests
- Files to modify: `FosSecurityContext.java`, `FosJwtConverter.java`, SDK security tests
- Exact change to make: Extract roles from top-level `roles`, `realm_access.roles`, and `resource_access.<client>.roles`. Normalize only if existing policy expects normalized names; preserve `ROLE_` values because OPA policies use `ROLE_HEAD_COACH` style.
- Safety rule: Do not remove existing top-level role support.
- Verification command: `mvn -pl fos-sdk/sdk-security -am -DskipTests compile`
- Expected result: SDK security compiles.
- What to do if verification fails: Revert extraction change and add focused tests before retrying.

### Step 4: Wire converter only where needed
- Objective: Ensure Spring Security authorities match extracted roles.
- Files to inspect: Gateway, governance, and workspace security configs
- Files to modify: Security config classes only if authorities are actually used by annotations/rules
- Exact change to make: If backend only uses `FosSecurityContext`, wiring converter may not be required. If needed, configure resource server JWT authentication converter in servlet apps and the reactive equivalent in gateway.
- Safety rule: Do not break JWT validation or public health/callback endpoints.
- Verification command: `mvn compile -DskipTests`
- Expected result: All backend modules compile.
- What to do if verification fails: Revert converter wiring and keep claim extraction in `FosSecurityContext`.

## 9. Verification Commands
- `docker compose config`
- `mvn -pl fos-sdk/sdk-security -am -DskipTests compile`
- `mvn compile -DskipTests`
- `npm run build`
- `curl http://localhost:8180/realms/fos/.well-known/openid-configuration` after containers are running

## 10. Acceptance Criteria
- [ ] Keycloak realm import exists and is mounted in compose.
- [ ] Realm/client settings match Angular environment values.
- [ ] Backend supports standard Keycloak role claim locations.
- [ ] Club claim strategy is documented or implemented.
- [ ] User knows to refresh browser session after mapper changes.

## 11. Rollback Plan
Remove the Keycloak import mount/command changes and restore previous security Java files from Git. If a local Keycloak DB was already initialized without import, the user may need to recreate the realm manually or reset local Keycloak data with explicit approval.

## 12. Notes For The Execution Agent
Do not invent real production Keycloak configuration. Keep import local-dev focused. Coordinate with `04-gateway-workspace-governance-plan.md` for policy behavior.
