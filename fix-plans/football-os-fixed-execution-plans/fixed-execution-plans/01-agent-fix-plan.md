# Football OS Error Repair — Agent Fix Plan

## Purpose
This file contains only the tasks the coding agent is allowed to attempt automatically.

Do not ask the user questions unless a task explicitly references `02-manual-fixes.md` and cannot continue without the user's local input.

Each task has:

- objective
- files to inspect
- files allowed to modify
- exact implementation instruction
- verification command
- expected result
- stop condition

---

# 1. Environment Templates And Profiles

## 1.1 Fix committed env template placeholders

### Objective
Make committed environment templates safe, valid, and clear without touching ignored local secret files.

### Inspect
```text
.env.example
.env.dev.example
.gitignore
README.md
docker-compose.yml
docker-compose.infra.yml
docker-compose.noauth.yml
fos-gateway/src/main/resources/application.yml
fos-gateway/src/main/resources/application-dev.yml
fos-gateway/src/main/resources/application-staging.yml
fos-workspace-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application-dev.yml
fos-workspace-frontend/src/environments/environment.ts
fos-workspace-frontend/src/environments/environment.development.ts
```

### Allowed To Modify
```text
.env.example
.env.dev.example
README.md
docker-compose.yml
docker-compose.infra.yml
docker-compose.noauth.yml
fos-gateway/src/main/resources/application.yml
fos-gateway/src/main/resources/application-dev.yml
fos-gateway/src/main/resources/application-staging.yml
fos-workspace-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application-dev.yml
fos-workspace-frontend/src/environments/environment.ts
fos-workspace-frontend/src/environments/environment.development.ts
```

### Do Not Modify
```text
.env
.env.dev
```

### Implementation
1. In `.env.example`, ensure `ONLYOFFICE_JWT_SECRET` is at least 32 characters.
2. Replace weak placeholders such as `change-me-onlyoffice-secret` with safe local placeholders such as:

```env
ONLYOFFICE_JWT_SECRET=local-onlyoffice-jwt-secret-32bytes-minimum
```

3. Ensure `.env.example` clearly separates these modes:
   - full Docker mode
   - hybrid host-backend mode
   - no-auth local mode
4. If `.env.dev.example` does not exist, create it.
5. `.env.dev.example` must contain non-secret local defaults only.
6. Keep `.env` and `.env.dev` ignored.
7. Do not copy any value from ignored local env files into committed templates.

### Verification
PowerShell:

```powershell
Select-String -Path .env.example -Pattern "ONLYOFFICE_JWT_SECRET|change-me-onlyoffice-secret"
git check-ignore -v .env .env.dev
Test-Path .env.dev.example
```

Bash equivalent:

```bash
grep -n "ONLYOFFICE_JWT_SECRET\|change-me-onlyoffice-secret" .env.example
git check-ignore -v .env .env.dev
test -f .env.dev.example
```

### Expected Result
- `change-me-onlyoffice-secret` is gone.
- `ONLYOFFICE_JWT_SECRET` is present and 32+ characters in templates.
- `.env` and `.env.dev` remain ignored.
- `.env.dev.example` exists.

### Stop Condition
Stop if `.env` or `.env.dev` must be edited to continue. That is manual item `MANUAL-004`.

---

## 1.2 Normalize PostgreSQL env variable naming

### Objective
Remove confusion between `DB_USER` / `DB_PASS` and `POSTGRES_USER` / `POSTGRES_PASSWORD`.

### Inspect
```text
.env.example
.env.dev.example
docker-compose.yml
docker-compose.infra.yml
fos-governance-service/src/main/resources/application.yml
README.md
```

### Allowed To Modify
```text
.env.example
.env.dev.example
docker-compose.yml
docker-compose.infra.yml
README.md
```

### Implementation
1. Use `POSTGRES_USER` and `POSTGRES_PASSWORD` as the canonical variable names.
2. Update compose files to use the canonical names.
3. If backward compatibility is needed, use simple fallback only if `docker compose config` proves it works.
4. Do not use complex nested interpolation if Compose rejects it.
5. Document the canonical names in README.

### Verification
```bash
docker compose config
```

### Expected Result
Compose config resolves without missing variable warnings or interpolation errors.

### Stop Condition
If Compose rejects fallback interpolation, use only the canonical variables and document migration in README.

---

## 1.3 Make gateway CORS configurable without wildcard credentials

### Objective
Allow local frontend/LAN origins through env configuration without unsafe wildcard credentials.

### Inspect
```text
fos-gateway/src/main/resources/application.yml
fos-gateway/src/main/resources/application-dev.yml
fos-gateway/src/main/resources/application-staging.yml
.env.example
.env.dev.example
```

### Allowed To Modify
```text
fos-gateway/src/main/resources/application.yml
fos-gateway/src/main/resources/application-dev.yml
fos-gateway/src/main/resources/application-staging.yml
.env.example
.env.dev.example
README.md
```

### Implementation
1. Replace hardcoded local-only origins with env-driven values.
2. Preferred value:

```yaml
allowedOrigins: ${CORS_ALLOWED_ORIGINS:http://localhost:4200}
```

3. If the project uses `allowedOriginPatterns`, use:

```yaml
allowedOriginPatterns: ${CORS_ALLOWED_ORIGIN_PATTERNS:http://localhost:4200}
```

4. Do not use `*` with credentials enabled.
5. Add the selected env variable to `.env.example` and `.env.dev.example`.

### Verification
```bash
mvn -pl fos-gateway -am -DskipTests compile
```

### Expected Result
Gateway compiles and CORS remains restricted by default.

### Stop Condition
If Spring binding fails, revert the CORS change and document the limitation in README.

---

# 2. Docker Compose, OPA, And No-Auth Consistency

## 2.1 Add a real reachable OPA service to full compose

### Objective
Fix the mismatch where governance points to OPA but full Docker Compose does not start OPA.

### Inspect
```text
docker-compose.yml
docker-compose.infra.yml
docker-compose.noauth.yml
fos-governance-service/src/main/resources/application.yml
fos-governance-service/src/main/resources/opa/
opa-mock/
```

### Allowed To Modify
```text
docker-compose.yml
docker-compose.infra.yml
.env.example
README.md
```

### Implementation
1. Prefer a real OPA container in full Docker Compose.
2. Mount existing Rego policy files from:

```text
fos-governance-service/src/main/resources/opa/
```

3. Configure governance to use the OPA service hostname inside Docker, for example:

```env
OPA_URL=http://opa:8181
```

4. Do not silently switch full compose to allow-all mock policy.
5. Only use `opa-mock` in no-auth or explicitly local mock modes.

### Verification
```bash
docker compose config
```

### Expected Result
- Full compose contains a policy service.
- `OPA_URL` resolves to a Docker service hostname.
- Governance no longer points to a missing OPA endpoint.

### Stop Condition
If real OPA cannot load the existing Rego files, stop and report the OPA/Rego error. Do not replace real policy with allow-all mock unless the user approves.

---

## 2.2 Align no-auth overlay across gateway, workspace, and governance

### Objective
Prevent no-auth mode from disabling auth only in some services while workspace still calls secured governance.

### Inspect
```text
docker-compose.noauth.yml
fos-gateway/src/main/resources/application.yml
fos-governance-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application.yml
```

### Allowed To Modify
```text
docker-compose.noauth.yml
README.md
```

### Implementation
1. In `docker-compose.noauth.yml`, set this for every backend service intended to run without JWT:

```env
FOS_SECURITY_ENABLED=false
```

2. Apply it consistently to:
   - gateway
   - workspace
   - governance
3. Do not modify the default secured `docker-compose.yml` security behavior.
4. Document clearly that no-auth is local-only.

### Verification
```bash
docker compose -f docker-compose.yml -f docker-compose.noauth.yml config
```

### Expected Result
The merged config shows `FOS_SECURITY_ENABLED=false` for gateway, workspace, and governance only in the no-auth overlay.

### Stop Condition
Stop if any service uses a different security toggle name and cannot be confirmed from its application config.

---

## 2.3 Keep OpenSearch unchanged unless user decides

### Objective
Avoid risky cleanup decisions.

### Implementation
Do not remove OpenSearch. Do not profile it. Do not delete volumes. Leave it as-is unless the user explicitly gives a decision from `MANUAL-009`.

### Verification
```bash
docker compose config
```

### Expected Result
No destructive OpenSearch change is made.

---

# 3. Keycloak Realm And Role Claims

## 3.1 Add reproducible local Keycloak realm import

### Objective
Make local Keycloak startup reproducible instead of starting with an empty realm.

### Inspect
```text
docker-compose.yml
docker-compose.infra.yml
fos-workspace-frontend/src/environments/environment.ts
fos-workspace-frontend/src/environments/environment.development.ts
README.md
```

### Allowed To Modify
```text
keycloak/fos-realm.local.json
docker-compose.yml
docker-compose.infra.yml
README.md
```

### Implementation
1. Create:

```text
keycloak/fos-realm.local.json
```

2. Realm name must be:

```text
fos
```

3. Add a public Angular client:

```text
fos-workspace-frontend
```

4. Configure client for Authorization Code with PKCE.
5. Redirect URI:

```text
http://localhost:4200/*
```

6. Web origin:

```text
http://localhost:4200
```

7. Include local development roles only, for example:

```text
ROLE_HEAD_COACH
ROLE_CLUB_ADMIN
ROLE_MEDICAL_STAFF
ROLE_ANALYST
```

8. Do not include real production users or real passwords.
9. Mount the import file into the Keycloak container:

```text
/opt/keycloak/data/import/fos-realm.local.json:ro
```

10. Start Keycloak with import enabled:

```text
start-dev --import-realm
```

### Verification
```bash
docker compose config
grep -n "fos-workspace-frontend\|ROLE_HEAD_COACH\|fos" keycloak/fos-realm.local.json
```

### Expected Result
Compose config includes the import mount and Keycloak command. Realm JSON contains realm, client, and local roles.

### Stop Condition
If the local Keycloak database was already initialized and import does not apply, record `MANUAL-002` instead of deleting Keycloak data.

---

## 3.2 Support standard Keycloak role claim locations in backend SDK

### Objective
Fix backend role extraction so standard Keycloak tokens authorize correctly.

### Inspect
```text
fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java
fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java
fos-sdk/sdk-security/src/test/java/com/fos/sdk/security/
```

### Allowed To Modify
```text
fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java
fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java
fos-sdk/sdk-security/src/test/java/com/fos/sdk/security/
```

### Implementation
1. Preserve existing support for top-level `roles`.
2. Add support for:

```text
realm_access.roles
resource_access.<client>.roles
```

3. Preserve `ROLE_` prefixes. Do not strip them if policies expect values like `ROLE_HEAD_COACH`.
4. Add or update focused unit tests for all three claim locations:
   - top-level `roles`
   - `realm_access.roles`
   - `resource_access.fos-workspace-frontend.roles`

### Verification
```bash
mvn -pl fos-sdk/sdk-security -am test
```

If tests are too slow or blocked, run:

```bash
mvn -pl fos-sdk/sdk-security -am -DskipTests compile
```

### Expected Result
SDK security module passes tests or at least compiles if tests are blocked by local environment.

### Stop Condition
Stop if changing role extraction would break existing top-level role support.

---

# 4. Gateway, Workspace, And Governance Flow

## 4.1 Verify and fix gateway route coverage

### Objective
Ensure the frontend can call the gateway for all workspace and governance APIs.

### Inspect
```text
fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java
fos-workspace-frontend/src/app/
```

### Allowed To Modify
```text
fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java
```

### Implementation
1. Ensure workspace routes include:

```text
/api/v1/documents/**
/api/v1/events/**
/api/v1/profiles/**
/api/v1/notifications/**
/api/v1/search/**
/api/v1/onlyoffice/**
```

2. Ensure governance routes include:

```text
/api/v1/actors/**
/api/v1/players/**
/api/v1/teams/**
/api/v1/policy/**
/api/v1/signals/**
/api/v1/identity/**
```

3. Do not route frontend directly to workspace port `8082`.

### Verification
```bash
mvn -pl fos-gateway -am -DskipTests compile
```

### Expected Result
Gateway compiles and all required route families are present.

### Stop Condition
Stop if route path conventions differ from the controllers and cannot be confirmed.

---

## 4.2 Fix upload notification actor semantics

### Objective
Prevent document upload notifications from using a club owner ID as the uploader actor ID.

### Inspect
```text
fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java
fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java
fos-workspace-service/src/test/java/com/fos/workspace/
```

### Allowed To Modify
```text
fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java
fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java
fos-workspace-service/src/test/java/com/fos/workspace/
```

### Implementation
1. When emitting document upload signals, include an explicit uploader actor field, for example:

```text
uploaderActorId
```

2. In `WorkspaceKafkaConsumer`, read `uploaderActorId` first.
3. Keep backward compatibility by falling back to legacy `actorRef` only if `uploaderActorId` is absent.
4. Do not change the consumer base class.
5. `WorkspaceKafkaConsumer` must still extend `AbstractFosConsumer`.

### Verification
```bash
mvn -pl fos-workspace-service -am -DskipTests compile
```

### Expected Result
Workspace compiles and notification actor semantics are explicit.

### Stop Condition
Stop if the current event payload structure cannot support adding metadata without breaking consumers.

---

## 4.3 Verify architecture boundaries

### Objective
Ensure no forbidden direct dependencies were introduced.

### Inspect
```text
fos-workspace-service/src/main/java
fos-sdk/
```

### Modify
None unless a concrete violation is found.

### Verification
```bash
rg -n "com\.fos\.governance|OpaClient|MongoClient|MinioClient|S3Client" fos-workspace-service/src/main/java
rg -n "@KafkaListener" fos-workspace-service/src/main/java
```

### Expected Result
- Workspace does not import governance internals.
- Workspace does not call OPA directly.
- Workspace does not instantiate raw storage clients.
- Kafka listener usage remains inside valid consumer infrastructure.

### Stop Condition
If violations exist, report exact file and line. Do not perform a broad refactor without a focused fix.

---

# 5. MinIO Storage And OnlyOffice Integration

## 5.1 Clarify storage provider modes

### Objective
Make storage behavior explicit for `noop` development mode and `minio` real document mode.

### Inspect
```text
.env.example
.env.dev.example
README.md
docker-compose.yml
docker-compose.infra.yml
fos-workspace-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application-dev.yml
```

### Allowed To Modify
```text
.env.example
.env.dev.example
README.md
docker-compose.yml
docker-compose.infra.yml
fos-workspace-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application-dev.yml
```

### Implementation
1. Document these modes:

```text
STORAGE_PROVIDER=noop  -> metadata-only/lightweight host development
STORAGE_PROVIDER=minio -> real upload, download, OnlyOffice editing
```

2. In Docker mode, internal backend endpoint should use the Docker service name, for example:

```env
MINIO_ENDPOINT=http://minio:9000
```

3. Public/browser/OnlyOffice endpoint should be documented separately, for example:

```env
MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000
```

4. Do not delete buckets or objects.
5. Do not bypass `StoragePort`.

### Verification
```bash
grep -n "STORAGE_PROVIDER\|MINIO_ENDPOINT\|MINIO_PUBLIC_ENDPOINT\|MINIO_BUCKET" .env.example .env.dev.example README.md
mvn -pl fos-sdk/sdk-storage -am -DskipTests compile
```

### Expected Result
Storage modes and endpoints are clear. SDK storage compiles.

### Stop Condition
Stop if real MinIO object testing is needed. That is `MANUAL-008` or `MANUAL-010`.

---

## 5.2 Fix OnlyOffice env defaults and callback base URL

### Objective
Fix OnlyOffice JWT length and callback URL mode confusion.

### Inspect
```text
.env.example
.env.dev.example
docker-compose.yml
docker-compose.infra.yml
fos-workspace-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application-dev.yml
README.md
```

### Allowed To Modify
```text
.env.example
.env.dev.example
docker-compose.yml
docker-compose.infra.yml
fos-workspace-service/src/main/resources/application.yml
fos-workspace-service/src/main/resources/application-dev.yml
README.md
```

### Implementation
1. Ensure committed template secret is 32+ characters.
2. Full Docker callback base URL should use the internal gateway service name:

```env
ONLYOFFICE_CALLBACK_BASE_URL=http://fos-gateway:8080
```

3. Hybrid host-backend mode may use:

```env
ONLYOFFICE_CALLBACK_BASE_URL=http://host.docker.internal:8080
```

4. Document the difference clearly.
5. Do not edit local `.env`.

### Verification
```bash
docker compose config | grep -i ONLYOFFICE
```

### Expected Result
Full Docker mode resolves OnlyOffice callback through the gateway service name unless local ignored `.env` intentionally overrides it.

### Stop Condition
If local `.env` overrides the value incorrectly, record `MANUAL-004` instead of editing it.

---

## 5.3 Reject unsigned OnlyOffice callbacks when JWT is enabled

### Objective
Close the security bug where unsigned callbacks are accepted even when JWT is enabled.

### Inspect
```text
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java
fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/
```

### Allowed To Modify
```text
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java
fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/
```

### Implementation
1. Find the callback payload resolution logic.
2. If `jwtEnabled=true` and the callback token is missing or blank, reject the callback.
3. Keep unsigned callback support only when `jwtEnabled=false`.
4. Do not log secrets or full JWT values.
5. Add/update tests for:
   - JWT enabled + signed callback accepted
   - JWT enabled + unsigned callback rejected
   - JWT disabled + unsigned callback accepted

### Verification
```bash
mvn -pl fos-workspace-service -am test
```

If full tests are blocked, run:

```bash
mvn -pl fos-workspace-service -am -DskipTests compile
```

### Expected Result
Workspace tests pass or compile succeeds if tests are blocked. Callback security behavior is enforced.

### Stop Condition
Stop if rejecting callbacks requires changing controller response format and OnlyOffice compatibility is uncertain.

---

## 5.4 Align frontend and backend supported file types

### Objective
Stop the frontend from offering OnlyOffice edit paths for formats not supported by backend config.

### Inspect
```text
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java
fos-workspace-frontend/src/app/features/documents/
fos-workspace-frontend/src/app/features/workspace-onlyoffice/
```

### Allowed To Modify
```text
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java
fos-workspace-frontend/src/app/features/documents/
fos-workspace-frontend/src/app/features/workspace-onlyoffice/
fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/
```

### Implementation
1. Prefer the safe option: reduce frontend-supported OnlyOffice extensions to the backend-supported set.
2. Use this conservative initial set unless backend already proves more support:

```text
docx
xlsx
pptx
pdf
```

3. Do not claim support for `doc`, `xls`, `ppt`, `txt`, `odt`, `ods`, or `odp` unless backend content type resolution and actual OnlyOffice behavior are implemented and tested.

### Verification
```bash
mvn -pl fos-workspace-service -am -DskipTests compile
cd fos-workspace-frontend && npm run build
```

### Expected Result
Backend compiles and frontend builds.

### Stop Condition
Stop if the frontend file support list is generated dynamically from backend and cannot be safely changed locally.

---

# 6. Frontend Cleanup

## 6.1 Enforce gateway-only API calls

### Objective
Ensure Angular never calls workspace directly at port `8082`.

### Inspect
```text
fos-workspace-frontend/src/
```

### Allowed To Modify
```text
fos-workspace-frontend/src/app/
fos-workspace-frontend/src/environments/environment.ts
fos-workspace-frontend/src/environments/environment.development.ts
```

### Implementation
1. Search for direct workspace calls:

```text
localhost:8082
http://localhost:8082
```

2. Replace runtime usage with the gateway base URL from environment config.
3. Default local development gateway base URL:

```text
http://localhost:8080
```

4. Do not hardcode service ports inside Angular services.

### Verification
```bash
rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend/src
cd fos-workspace-frontend && npm run build
```

### Expected Result
No runtime frontend source calls workspace `8082` directly. Angular build succeeds.

### Stop Condition
Stop if a direct `8082` reference is in documentation or tests only; report it instead of changing blindly.

---

## 6.2 Move fake UUIDs out of runtime code

### Objective
Prevent fake UUIDs from silently driving real runtime behavior.

### Inspect
```text
fos-workspace-frontend/src/app/
fos-workspace-frontend/src/environments/
fos-workspace-service/src/main/java/
```

### Allowed To Modify
```text
fos-workspace-frontend/src/app/
fos-workspace-frontend/src/environments/environment.development.ts
fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java
fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java
README.md
```

### Implementation
1. Search for known fake UUIDs:

```text
00000000-0000-0000-0000-000000000001
11111111-1111-1111-1111-111111111101
```

2. In frontend runtime code, remove hardcoded fake UUIDs.
3. If a real ID source does not exist yet, move the fallback to `environment.development.ts` with an explicit name:

```ts
devFallbackTeamId
devFallbackClubId
devFallbackActorId
```

4. Add comments stating these are local no-auth development fallbacks only.
5. Do not invent production IDs.

### Verification
```bash
rg -n "00000000-0000-0000-0000-000000000001|11111111-1111-1111-1111-111111111101" fos-workspace-frontend/src/app fos-workspace-service/src/main/java
cd fos-workspace-frontend && npm run build
mvn -pl fos-workspace-service -am -DskipTests compile
```

### Expected Result
Fake UUIDs are removed from hidden runtime logic or clearly isolated as dev-only fallback config.

### Stop Condition
If real IDs are required for correctness, record `MANUAL-003` or `MANUAL-010`.

---

## 6.3 Isolate mock data instead of deleting old UI

### Objective
Stop mock/demo data from masquerading as live data while avoiding risky deletion.

### Inspect
```text
fos-workspace-frontend/src/app/core/data/workspace-data.service.ts
fos-workspace-frontend/src/app/features/
fos-workspace-frontend/src/app/app.routes.ts
```

### Allowed To Modify
```text
fos-workspace-frontend/src/app/core/data/workspace-data.service.ts
fos-workspace-frontend/src/app/features/
fos-workspace-frontend/src/app/app.routes.ts
README.md
```

### Implementation
1. Find all uses of `WorkspaceDataService`.
2. If a backend API exists, use a real API service through the gateway.
3. If no backend API exists, keep mock behavior but mark it explicitly as development fallback.
4. Do not delete old folders or old UI components.
5. Do not redesign the frontend.

### Verification
```bash
rg -n "WorkspaceDataService" fos-workspace-frontend/src/app
cd fos-workspace-frontend && npm run build
```

### Expected Result
Mock data usage is either reduced or clearly labeled as dev-only. Build succeeds.

### Stop Condition
If deleting obsolete folders seems necessary, stop and record `MANUAL-007`.

---

# 7. Backend Cleanup

## 7.1 Add `-am` to host-run scripts

### Objective
Make host-run scripts work on clean machines by building required Maven modules automatically.

### Inspect
```text
run-gateway.sh
run-governance.sh
run-workspace.sh
Makefile
README.md
```

### Allowed To Modify
```text
run-gateway.sh
run-governance.sh
run-workspace.sh
Makefile
README.md
```

### Implementation
1. Add `-am` to Maven commands that run a specific module with `-pl`.
2. Example target form:

```bash
mvn -pl fos-workspace-service -am spring-boot:run
```

3. Do not change ports or profiles unless already wrong.

### Verification
```bash
rg -n "mvn .* -pl|mvn .*--projects|mvn" run-gateway.sh run-governance.sh run-workspace.sh Makefile
```

### Expected Result
Every module-specific Maven run command includes `-am`.

### Stop Condition
Stop if scripts use a non-Maven runner or are generated files.

---

## 7.2 Bound backend no-auth fallback IDs

### Objective
Make fallback IDs clearly local-dev only.

### Inspect
```text
fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java
fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java
```

### Allowed To Modify
```text
fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java
fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java
fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java
README.md
```

### Implementation
1. Do not remove no-auth fallbacks if local development depends on them.
2. Rename constants/comments so they clearly say local no-auth development fallback.
3. If simple and safe, make them configurable through properties:

```text
fos.dev.fallback-club-id
fos.dev.fallback-team-id
fos.dev.fallback-actor-id
```

4. Defaults must remain fake/local only.
5. Do not invent real business IDs.

### Verification
```bash
mvn -pl fos-workspace-service -am -DskipTests compile
```

### Expected Result
Workspace compiles and fallback IDs are clearly bounded to local no-auth development.

### Stop Condition
If real IDs are needed, record `MANUAL-003`.

---

# 8. Final Agent Report

At the end, create a final report in the chat or in a file named:

```text
fix-plans/execution-results.md
```

The report must include:

```md
# Execution Results

## Changed Files
- ...

## Commands Run
| Command | Result |
|---|---|
| ... | pass/fail/blocked |

## Issues Fixed
- ...

## Manual Blockers Remaining
- MANUAL-...

## Risks / Notes
- ...

## Next Recommended Step
- ...
```
