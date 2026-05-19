# Football OS Error Repair — Verification Checklist

## Purpose
Run this only after `01-agent-fix-plan.md` has been executed or blocked by documented manual items.

Verification must not be used as a place to improvise new fixes. If a check fails, identify the owning category, record the failure, and stop or return to that category plan.

---

# 1. Git Scope Verification

## Command
```bash
git status --short
```

## Pass Criteria
- Only intended files changed.
- No forbidden folders changed:

```text
report/
reports/
rapport/
rapports/
target/
node_modules/
dist/
.git/
```

## If Fails
Stop and report unexpected changed paths. Do not revert user files without permission.

---

# 2. Static Search Verification

## 2.1 Frontend must not call workspace directly

### Command
```bash
rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend/src
```

### Pass Criteria
No runtime Angular source calls workspace port `8082` directly.

Documentation or test-only references may remain if clearly non-runtime.

---

## 2.2 Fake UUIDs must not be hidden in runtime code

### Command
```bash
rg -n "00000000-0000-0000-0000-000000000001|11111111-1111-1111-1111-111111111101" fos-workspace-frontend/src/app fos-workspace-service/src/main/java
```

### Pass Criteria
Fake UUIDs are either absent from runtime code or clearly named as local no-auth development fallbacks.

---

## 2.3 Workspace must not depend on governance internals or raw infra clients

### Command
```bash
rg -n "com\.fos\.governance|OpaClient|MongoClient|MinioClient|S3Client" fos-workspace-service/src/main/java
```

### Pass Criteria
No forbidden direct dependencies in workspace domain/application code.

---

## 2.4 Kafka consumers must remain inside expected pattern

### Command
```bash
rg -n "@KafkaListener|extends AbstractFosConsumer" fos-workspace-service/src/main/java fos-governance-service/src/main/java
```

### Pass Criteria
Kafka listener usage remains compatible with the shared consumer pattern.

---

# 3. Backend Compile Verification

## Command
```bash
mvn compile -DskipTests
```

## Pass Criteria
Full Maven reactor compile succeeds.

## If Fails
Return to the category that owns the failing module:

- `fos-sdk/sdk-security` -> Keycloak/role claims
- `fos-gateway` -> gateway/CORS/routes
- `fos-governance-service` -> OPA/security/governance config
- `fos-workspace-service` -> workspace/storage/OnlyOffice/notification fixes

---

# 4. Focused Backend Test Verification

Run focused tests where changes were made.

## SDK Security
```bash
mvn -pl fos-sdk/sdk-security -am test
```

## Workspace Service
```bash
mvn -pl fos-workspace-service -am test
```

## Gateway
```bash
mvn -pl fos-gateway -am test
```

## Pass Criteria
Relevant tests pass.

## If Fails
Record exact failing test class, method, and error message.

---

# 5. Frontend Build Verification

## Command
```bash
cd fos-workspace-frontend
npm run build
```

## Pass Criteria
Angular production build succeeds.

Warnings are acceptable only if documented in the final report.

## If Fails
Return to frontend cleanup tasks.

---

# 6. Docker Compose Config Verification

These commands do not start containers. They only validate configuration.

## 6.1 Full Compose
```bash
docker compose config
```

### Pass Criteria
- Config resolves without missing variables.
- OPA service exists or policy endpoint is explicitly reachable.
- OnlyOffice callback defaults are visible.

---

## 6.2 Infra / Hybrid Compose
```bash
docker compose --env-file .env.dev -f docker-compose.infra.yml config
```

### Pass Criteria
- Config resolves.
- Hybrid env values are valid.
- `.env.dev` remains ignored.

### If Blocked
If `.env.dev` does not exist, mark blocked by local setup. Do not create secrets automatically.

---

## 6.3 No-Auth Overlay
```bash
docker compose -f docker-compose.yml -f docker-compose.noauth.yml config
```

### Pass Criteria
- Gateway, workspace, and governance all resolve `FOS_SECURITY_ENABLED=false` in no-auth mode.
- Default full compose remains secured.

---

# 7. Optional Running Stack Health Verification

Run only if Docker Desktop is running. If Docker is unavailable, record `MANUAL-001`.

## Start Stack
Use the user's preferred mode. Do not use `down -v`.

Full stack:

```bash
docker compose up -d
```

No-auth stack:

```bash
docker compose -f docker-compose.yml -f docker-compose.noauth.yml up -d
```

## Check Containers
```bash
docker compose ps
```

## Health Checks
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

## Pass Criteria
Gateway, governance, and workspace return healthy or expected health JSON.

## If Fails
Record failing service and collect logs:

```bash
docker compose logs --tail=200 <service-name>
```

Do not delete volumes.

---

# 8. Keycloak Verification

Run only after Keycloak is started.

## Command
```bash
curl http://localhost:8180/realms/fos/.well-known/openid-configuration
```

## Pass Criteria
Returns JSON for realm `fos`.

## If Fails
Record `MANUAL-002` unless the failure is a simple compose config issue.

---

# 9. OnlyOffice Verification

Run only after OnlyOffice container is started.

## API Script Check
```bash
curl -I http://localhost:8084/web-apps/apps/api/documents/api.js
```

## Pass Criteria
Returns HTTP `200` or a successful response indicating `api.js` is reachable.

## Real Document Check
Only run if the user provided valid data from `MANUAL-008` and `MANUAL-010`.

Expected flow:

1. Log in or use no-auth mode as configured.
2. Call the gateway, not workspace directly.
3. Request OnlyOffice config for a real document UUID.
4. Open document in frontend.
5. Confirm save callback creates a new version or successful save response.

## If Fails
Record:

- endpoint URL
- status code
- response body
- browser console/network error if frontend involved
- related document UUID with secrets omitted

---

# 10. Final Report Template

Create or output:

```text
fix-plans/execution-results.md
```

Use this template:

```md
# Execution Results

## Summary
- Status: pass / partial / blocked / failed
- Date:
- Branch:

## Changed Files
- ...

## Commands Run
| Command | Result | Notes |
|---|---|---|
| git status --short | pass/fail | ... |
| mvn compile -DskipTests | pass/fail | ... |
| npm run build | pass/fail | ... |
| docker compose config | pass/fail/blocked | ... |

## Issues Fixed
- ...

## Issues Blocked By Manual Action
- MANUAL-001: ...
- MANUAL-004: ...

## Remaining Risks
- ...

## Next Step For User
- ...
```
