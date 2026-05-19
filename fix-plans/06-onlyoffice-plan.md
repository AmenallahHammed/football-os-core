# 06 — OnlyOffice

## 1. Purpose
Fix OnlyOffice JWT, callback security, URL mode separation, and frontend/backend file-type compatibility.

## 2. Source Errors From erreurs.md
- ERR-003: OnlyOffice JWT secret is too short in full local env
- ERR-005: OnlyOffice callback accepts unsigned payload when JWT is enabled
- ERR-006: Full Docker callback URL is overridden to host path
- Section 9: ONLYOFFICE Integration Problems
- Section 11: OnlyOffice placeholder values
- Section 12: OnlyOffice secret/callback/file-type mismatches

## 3. Classification
- Fix `.env.example` and compose defaults: Agent-fixable, coordinated with plan 01.
- Fix local `.env` secret: Manual-only; see MANUAL-004.
- Enforce signed callback when JWT enabled: Agent-fixable.
- Verify real editor load/save: Mixed; needs MANUAL-008 and MANUAL-010.
- Set LAN public URL: Manual-only for LAN; see MANUAL-005.

## 4. Files Allowed To Modify
- `.env.example`
- `.env.dev.example`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java`
- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- `fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/`
- `fos-workspace-frontend/src/app/features/workspace-onlyoffice/`
- `fos-workspace-frontend/src/app/features/documents/documents.component.ts`
- `README.md`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not weaken public callback access at gateway without understanding OnlyOffice callback requirements.
- Do not bypass `StoragePort`.

## 6. Current Problem Summary
OnlyOffice config generation can fail due to short JWT secrets. Callback security accepts unsigned payloads even when JWT is enabled. Full Docker callback URL uses a host route instead of the internal gateway service name. Frontend accepts more extensions than backend config supports.

## 7. Target State
OnlyOffice uses a 32+ byte shared JWT secret, signed callbacks are required when JWT is enabled, callback URLs are correct per Docker mode, and frontend file-type options match backend support or backend support is expanded deliberately.

## 8. Step-by-Step Execution Plan
### Step 1: Align OnlyOffice env defaults
- Objective: Ensure committed templates and compose defaults use valid secrets and correct URLs.
- Files to inspect: `.env.example`, `docker-compose.yml`, workspace application YAML
- Files to modify: `.env.example`, `docker-compose.yml`, README
- Exact change to make: Use `ONLYOFFICE_CALLBACK_BASE_URL=http://fos-gateway:8080` in full Docker documentation/defaults. Keep `http://host.docker.internal:8080` documented only for hybrid host-run backend. Ensure template secret is 32+ bytes.
- Safety rule: Do not edit local `.env` without explicit user request.
- Verification command: `docker compose config | Select-String "ONLYOFFICE"`
- Expected result: Full compose resolves callback base to `http://fos-gateway:8080` unless user local `.env` intentionally overrides it.
- What to do if verification fails: Recheck `.env` override and document manual correction.

### Step 2: Reject unsigned callbacks when JWT is enabled
- Objective: Close callback forgery risk.
- Files to inspect: `OnlyOfficeSaveHandler.java`, OnlyOffice callback tests
- Files to modify: `OnlyOfficeSaveHandler.java`, tests in `fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/`
- Exact change to make: In `resolveCallbackPayload`, if `jwtEnabled` is true and missing/blank token, throw or return an error path that prevents save processing. Keep unsigned payload support only when `jwtEnabled=false`.
- Safety rule: Keep controller response compatible with OnlyOffice; avoid leaking secrets in logs.
- Verification command: `mvn -pl fos-workspace-service -am -DskipTests compile`
- Expected result: Workspace compiles.
- What to do if verification fails: Revert handler change and add focused unit test first.

### Step 3: Align file-type support
- Objective: Avoid frontend offering unsupported edit paths.
- Files to inspect: `OnlyOfficeConfigService.java`, `documents.component.ts`, `workspace-onlyoffice-editor.component.ts`
- Files to modify: Either backend config service or frontend supported extension sets
- Exact change to make: Choose one: either reduce frontend OnlyOffice-supported extensions to `docx`, `xlsx`, `pptx`, `pdf`, or expand backend content-type resolution and tests for `doc`, `xls`, `ppt`, `txt`, `odt`, `ods`, `odp`. Prefer reducing frontend first unless backend conversion support is confirmed.
- Safety rule: Do not claim support for formats not tested with Document Server.
- Verification command: `npm run build; mvn -pl fos-workspace-service -am -DskipTests compile`
- Expected result: Build and compile succeed.
- What to do if verification fails: Revert the file-type change and document unsupported formats.

### Step 4: Verify callback route exposure
- Objective: Keep OnlyOffice callback reachable while secured APIs remain protected.
- Files to inspect: Gateway and workspace security configs
- Files to modify: Only if callback route is missing from public allow-list
- Exact change to make: Ensure `/api/v1/onlyoffice/callback/**` and `/api/v1/onlyoffice/health` remain public in gateway and workspace, while `/api/v1/onlyoffice/config` remains protected when security is enabled.
- Safety rule: Do not make all OnlyOffice endpoints public.
- Verification command: `mvn -pl fos-gateway,fos-workspace-service -am -DskipTests compile`
- Expected result: Both services compile.
- What to do if verification fails: Revert security config changes.

## 9. Verification Commands
- `docker compose config | Select-String "ONLYOFFICE"`
- `mvn -pl fos-workspace-service -am -DskipTests compile`
- `mvn -pl fos-gateway,fos-workspace-service -am -DskipTests compile`
- `npm run build`
- `curl -I http://localhost:8084/web-apps/apps/api/documents/api.js` after containers run

## 10. Acceptance Criteria
- [ ] Committed OnlyOffice secret placeholder is at least 32 bytes.
- [ ] Signed callbacks are required when JWT is enabled.
- [ ] Full Docker callback URL uses gateway service name or is explicitly overridden by user.
- [ ] Frontend and backend agree on supported file types.
- [ ] OnlyOffice config endpoint remains protected when security is enabled.

## 11. Rollback Plan
Restore OnlyOffice Java classes, tests, frontend file-type lists, compose/env template changes, and README from Git. Do not delete document objects or database records.

## 12. Notes For The Execution Agent
Coordinate with plans 01 and 05. Do not test real document editing until user provides document UUID and MinIO object confirmation.
