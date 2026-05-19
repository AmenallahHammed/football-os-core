# Step 06 — OnlyOffice Integration

## 1. Purpose
Fix OnlyOffice local integration safety and consistency: JWT secret length, callback URL mode, signed callback enforcement, file type support alignment, public/protected endpoint boundaries, and editor load/save verification.

## 2. Errors Covered
- OnlyOffice JWT placeholder can be too short.
- Full Docker callback base URL may point to a host path instead of the internal gateway service.
- Callback handling may accept unsigned payloads when JWT is enabled.
- Frontend may offer file types backend config does not support.
- OnlyOffice callback and health may need public access, but config/protected APIs must not become public.
- Real editor load/save verification requires real document data and reachable public URLs.

## 3. Files To Inspect
- `.env.example`
- `.env.dev.example`
- `.env`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `README.md`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- `fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/`
- `fos-workspace-frontend/src/app/features/workspace-onlyoffice/`
- `fos-workspace-frontend/src/app/features/documents/`
- Gateway and workspace security configuration files.

## 4. Files Allowed To Modify
- `.env.example`
- `.env.dev.example`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `README.md`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- `fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/`
- `fos-workspace-frontend/src/app/features/workspace-onlyoffice/`
- `fos-workspace-frontend/src/app/features/documents/`
- Gateway/workspace security config only for OnlyOffice callback and health allow-list corrections.

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
- MinIO buckets or objects
- Document database records
- Any security config change that makes all OnlyOffice endpoints public.

## 6. Automatic Fixes To Perform
1. Align OnlyOffice env defaults.
   - Objective: Ensure committed templates use valid placeholders and correct mode-specific URLs.
   - Exact implementation instruction: Ensure committed `ONLYOFFICE_JWT_SECRET` placeholders are at least 32 characters. Set full Docker documentation/defaults to `ONLYOFFICE_CALLBACK_BASE_URL=http://fos-gateway:8080`. Document hybrid host-backend mode as `http://host.docker.internal:8080`.
   - Safety rule: Do not edit real `.env`.
   - Verification command: `docker compose config | Select-String "ONLYOFFICE"`
   - Expected result: Full compose resolves an internal gateway callback URL unless ignored local `.env` intentionally overrides it.
   - Stop condition if it fails: If local `.env` overrides the value incorrectly, record `MANUAL-004` instead of editing it.

2. Enforce signed callbacks when JWT is enabled.
   - Objective: Prevent unsigned callback processing in JWT-enabled mode.
   - Exact implementation instruction: In `OnlyOfficeSaveHandler`, reject missing or blank callback tokens when `jwtEnabled=true`. Preserve unsigned callback support only when `jwtEnabled=false`. Add/update tests for signed accepted, unsigned rejected with JWT enabled, and unsigned accepted with JWT disabled.
   - Safety rule: Do not log secrets or full JWT values; keep controller responses compatible with OnlyOffice.
   - Verification command: `mvn -pl fos-workspace-service -am test`
   - Expected result: Workspace tests pass and callback security is enforced.
   - Stop condition if it fails: If response format compatibility is uncertain, stop after compiling and document the risk.

3. Align supported file types.
   - Objective: Stop frontend from offering unsupported OnlyOffice edit paths.
   - Exact implementation instruction: Prefer the conservative set `docx`, `xlsx`, `pptx`, and `pdf` unless backend config and tests already prove more types. Remove unsupported edit actions from frontend runtime UI or add backend support only with tests and confirmed OnlyOffice behavior.
   - Safety rule: Do not claim support for `doc`, `xls`, `ppt`, `txt`, `odt`, `ods`, or `odp` unless tested.
   - Verification command: `mvn -pl fos-workspace-service -am -DskipTests compile; cd fos-workspace-frontend; npm run build`
   - Expected result: Backend compiles and frontend builds with matching file type behavior.
   - Stop condition if it fails: Revert the file-type change and document unsupported formats.

4. Verify OnlyOffice endpoint exposure.
   - Objective: Keep required callback/health endpoints reachable without exposing protected config APIs.
   - Exact implementation instruction: Ensure `/api/v1/onlyoffice/callback/**` and `/api/v1/onlyoffice/health` are public only if needed by Document Server. Ensure `/api/v1/onlyoffice/config` remains protected when security is enabled.
   - Safety rule: Do not make all OnlyOffice endpoints public.
   - Verification command: `mvn -pl fos-gateway,fos-workspace-service -am -DskipTests compile`
   - Expected result: Gateway and workspace compile.
   - Stop condition if it fails: Revert security allow-list changes and stop.

5. Prepare editor load/save smoke verification.
   - Objective: Define exact verification once real data exists.
   - Exact implementation instruction: Document that the future agent must load `http://localhost:8084/web-apps/apps/api/documents/api.js`, request OnlyOffice config through gateway for a real document UUID, open the editor, save, and verify a new version or save response.
   - Safety rule: Use test documents only and do not create fake production data.
   - Verification command: `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js`
   - Expected result: `api.js` is reachable when OnlyOffice is running.
   - Stop condition if it fails: Record Docker/OnlyOffice runtime blocker or `MANUAL-008`/`MANUAL-010`.

## 7. Manual-Only Blockers
- `MANUAL-004`: User must set real local OnlyOffice JWT secret in `.env`.
- `MANUAL-005`: User must set LAN/public URL for external device testing.
- `MANUAL-008`: User must verify real MinIO objects.
- `MANUAL-010`: User must provide real document UUID with uploaded current version.

## 8. Verification Commands
- `docker compose config | Select-String "ONLYOFFICE"`
- `mvn -pl fos-workspace-service -am test`
- `mvn -pl fos-gateway,fos-workspace-service -am -DskipTests compile`
- `cd fos-workspace-frontend; npm run build`
- `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js`

## 9. Acceptance Criteria
- [ ] Committed OnlyOffice JWT placeholder is at least 32 characters.
- [ ] Full Docker callback base URL uses the internal gateway service.
- [ ] Unsigned callbacks are rejected when JWT is enabled.
- [ ] Frontend and backend agree on supported file types.
- [ ] Only callback/health are public as needed; config remains protected.
- [ ] Editor load/save smoke is documented and blocked only by real data/runtime prerequisites.

## 10. Documentation To Update
Update `README.md` with OnlyOffice JWT requirements, callback URL by mode, endpoint exposure rules, supported file types, `api.js` check, and real document smoke prerequisites.

## 11. Rollback Plan
Revert env templates, compose, README, OnlyOffice Java classes, tests, frontend file-type changes, and security allow-list changes from this step. Do not delete document records, MinIO objects, or Docker volumes.
