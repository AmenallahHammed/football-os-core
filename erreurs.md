# Football OS Repository Audit — erreurs.md

## 1. Executive Summary
- Overall health: medium
- Main blockers:
  - Full Docker Compose auth cannot work out of the box because no Keycloak realm/client import exists for realm `fos` and client `fos-workspace-frontend`.
  - Full Docker Compose points governance to `http://host.docker.internal:8181` for OPA, but `docker-compose.yml` does not start an OPA service or mock.
  - Root `.env` and `.env.example` use `ONLYOFFICE_JWT_SECRET=change-me-onlyoffice-secret`, which is shorter than the backend's required 32 bytes and will break OnlyOffice config generation.
  - Security role extraction expects a top-level JWT `roles` claim, while frontend code also supports Keycloak `realm_access` and `resource_access`; without a mapper, backend policy checks will see `UNKNOWN`.
- Most urgent fixes:
  - Add/import Keycloak realm `fos`, public client `fos-workspace-frontend`, redirect URI `http://localhost:4200/*`, web origin `http://localhost:4200`, and required role/club claim mappers.
  - Add an OPA container/policy mount to the full compose stack, or route full local compose to `fos-opa-mock`.
  - Replace OnlyOffice JWT defaults with one shared secret at least 32 bytes long.
  - Make `.env` ownership clearer: `.env.example` as committed template, `.env` as ignored local runtime file.
- Whether the project can run locally right now:
  - Backend Java compiles.
  - Angular builds.
  - Hybrid no-auth local development is likely usable after infra is started.
  - Full secured Docker Compose is not complete for login/policy/OnlyOffice editing without manual Keycloak and OPA setup.

## 2. Scope and Ignored Folders
The audit scanned repository source/config/docs/build files except generated folders and intentionally ignored report-style folders.

Ignored intentionally:
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `build/`
- `.git/`
- `.idea/`
- `.vscode/`

Only `erreurs.md` was created/updated at the repository root.

## 3. Critical Errors
### ERR-001: Full Compose has Keycloak but no realm/client import
- Severity: critical
- Area: keycloak/security/frontend
- File(s): `docker-compose.yml`, `docker-compose.infra.yml`, `fos-workspace-frontend/src/environments/environment.ts`, `fos-workspace-frontend/src/environments/environment.development.ts`
- What is wrong: Keycloak starts empty with `command: start-dev`; no realm import JSON or mounted `/opt/keycloak/data/import` file exists. Frontend and backend expect realm `fos`.
- Why it matters: `http://localhost:8180/realms/fos` and JWKS `/realms/fos/protocol/openid-connect/certs` will not exist until configured manually.
- Evidence: Search found no Keycloak realm/client import file. Compose only starts the Keycloak image and DB settings.
- Suggested fix: Add a tracked local-dev realm import, mount it in compose, and start Keycloak with `start-dev --import-realm`.
- Safe to auto-fix later: no

### ERR-002: Full Compose references OPA but does not start OPA
- Severity: critical
- Area: docker/opa/security
- File(s): `docker-compose.yml`, `.env`, `.env.example`, `fos-governance-service/src/main/resources/application-staging.yml`
- What is wrong: Governance uses `OPA_URL`, and full compose resolves it to `http://host.docker.internal:8181`, but full compose has no `opa` or `fos-opa-mock` service.
- Why it matters: Workspace document/event operations call governance policy evaluation; governance then calls OPA. If no host service is listening on 8181, policy checks fail.
- Evidence: `docker compose config` showed `OPA_URL: http://host.docker.internal:8181`; only `docker-compose.infra.yml` and `docker-compose.noauth.yml` define `fos-opa-mock`.
- Suggested fix: Add a real OPA service with mounted Rego policies to `docker-compose.yml`, or include `fos-opa-mock` explicitly for local full-stack development.
- Safe to auto-fix later: yes

### ERR-003: OnlyOffice JWT secret is too short in full local env
- Severity: critical
- Area: onlyoffice/env
- File(s): `.env`, `.env.example`, `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- What is wrong: `.env` and `.env.example` set `ONLYOFFICE_JWT_SECRET=change-me-onlyoffice-secret`. The backend rejects secrets shorter than 32 bytes.
- Why it matters: `/api/v1/onlyoffice/config` will throw `OnlyOffice JWT secret must be at least 32 bytes`, so the document editor cannot open.
- Evidence: `OnlyOfficeConfigService.signConfig()` checks `jwtSecret.getBytes(...).length < 32`; `docker compose config` resolves the short secret into both `fos-workspace-service` and `onlyoffice`.
- Suggested fix: Use a single 32+ byte local secret, for example the longer value already present in `.env.dev`, and mirror it in `.env.example`.
- Safe to auto-fix later: yes

### ERR-004: Backend role extraction does not match standard Keycloak role claims
- Severity: high
- Area: security/keycloak/backend
- File(s): `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java`, `fos-sdk/sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java`, `fos-workspace-frontend/src/app/core/auth/auth.service.ts`
- What is wrong: Backend reads only a top-level `roles` claim. Frontend reads top-level `roles`, `realm_access.roles`, and `resource_access[client].roles`.
- Why it matters: With normal Keycloak tokens, backend workspace policy checks may use role `UNKNOWN` and deny legitimate users.
- Evidence: `FosSecurityContext.roles()` calls `jwt().getClaim("roles")`; no backend security config wires `FosJwtConverter`.
- Suggested fix: Either add Keycloak mappers for top-level `roles`, or update backend role extraction to support `realm_access` and `resource_access`.
- Safe to auto-fix later: yes

### ERR-005: OnlyOffice callback accepts unsigned payload when JWT is enabled
- Severity: high
- Area: onlyoffice/security
- File(s): `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java`, `fos-workspace-service/src/main/java/com/fos/workspace/WorkspaceApp.java`, `fos-gateway/src/main/java/com/fos/gateway/config/SecurityConfig.java`
- What is wrong: Callback endpoints are public, and `OnlyOfficeSaveHandler.resolveCallbackPayload()` logs a warning but still processes unsigned callback bodies when `jwtEnabled` is true.
- Why it matters: A forged callback with a known document UUID could trigger server-side download/upload logic.
- Evidence: Missing `token` returns the raw body even when `jwtEnabled` is true.
- Suggested fix: When JWT is enabled, reject unsigned callback payloads and return OnlyOffice-compatible error handling.
- Safe to auto-fix later: yes

### ERR-006: Full Docker callback URL is overridden to host path
- Severity: high
- Area: onlyoffice/docker/env
- File(s): `.env`, `.env.example`, `docker-compose.yml`
- What is wrong: Compose default for workspace callback is `http://fos-gateway:8080`, but `.env` overrides it to `http://host.docker.internal:8080`.
- Why it matters: In full Docker, the most reliable callback URL from OnlyOffice to gateway is the service name on `fos-net`. Host routing may work on some machines but is less deterministic.
- Evidence: `docker compose config` resolved `ONLYOFFICE_CALLBACK_BASE_URL: http://host.docker.internal:8080`.
- Suggested fix: Use `http://fos-gateway:8080` for full Docker compose; reserve `http://host.docker.internal:8080` for host-run backend/hybrid mode.
- Safe to auto-fix later: yes

## 4. Configuration and Environment Problems
- `.env` contains local secrets and is ignored by Git, which is correct. It is still present in the working tree and should remain local only.
- `.env.example` is tracked, but it contains secrets shaped like real credentials and a broken short OnlyOffice secret. It should contain safe placeholders that also satisfy length/format constraints.
- `.env.dev` is ignored by Git and used by `run-dev.sh`/README examples, but Docker Compose does not read it automatically.
- `REDIS_URL` and `KEYCLOAK_URL` in `.env` are not consumed by current Spring or compose config.
- `DB_USER`/`DB_PASS` are used by compose to populate `POSTGRES_USER`/`POSTGRES_PASSWORD`, but Spring staging uses `POSTGRES_USER`/`POSTGRES_PASSWORD`. This naming split is confusing.
- `STORAGE_PROVIDER` is in `.env.example` but missing from `.env`; full compose hardcodes it to `minio`.
- CORS is hardcoded to `http://localhost:4200` in gateway `application-dev.yml` and `application-staging.yml`; there is no env-based LAN origin support.
- Production Angular environment still has `gatewayBaseUrl: 'http://localhost:8080'`, so a production build is not deployable without rebuilding code.

## 5. .env File Analysis
| File | Purpose | Used by | Problems | Keep? | Recommendation |
|---|---|---|---|---|---|
| `.env` | Local full Docker Compose runtime values | Docker Compose automatically; Spring only through injected container env; Angular never | Contains local secrets; short OnlyOffice secret; OPA points to missing host service; callback URL better suited to hybrid than full Docker; has unused `KEYCLOAK_URL`, `REDIS_URL` | Yes, local only | Keep ignored by Git. Make it the local runtime source of truth for full Docker. Use 32+ byte OnlyOffice secret and `http://fos-gateway:8080` callback for full Docker. |
| `.env.dev` | Hybrid/infra dev overrides | Not auto-read by Compose; read by `run-dev.sh`; read by `docker compose --env-file .env.dev`; Spring only if exported | Ignored by Git but README assumes it exists; duplicates secrets with `.env`; omits many vars and relies on Spring defaults | Maybe | Either commit `.env.dev.example` or document that developers create it from `.env.example`. Keep actual `.env.dev` ignored. |
| `.env.example` | Tracked template | Human copy source only; Compose only if renamed or passed with `--env-file`; Spring/Angular do not read it | Contains broken short OnlyOffice secret; includes real-looking placeholder credentials; duplicates `.env` | Yes | Keep as committed template and make placeholders safe but valid. |

Recommended source of truth:
- For committed configuration: `.env.example`
- For local full Docker execution: `.env`
- For hybrid host-run development: `.env.dev` or a tracked `.env.dev.example`

Variables to keep:
- `HOST_LAN_IP`
- `ONLYOFFICE_PUBLIC_URL`
- `ONLYOFFICE_INTERNAL_URL`
- `ONLYOFFICE_CALLBACK_BASE_URL`
- `ONLYOFFICE_JWT_SECRET`
- `ONLYOFFICE_JWT_ENABLED`
- `ONLYOFFICE_JWT_HEADER`
- `ONLYOFFICE_JWT_IN_BODY`
- `MINIO_ENDPOINT`
- `MINIO_PUBLIC_ENDPOINT`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `KEYCLOAK_ADMIN`
- `KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_WEBHOOK_SECRET`
- `OPENSEARCH_INITIAL_ADMIN_PASSWORD`

Variables to rename:
- `DB_USER` -> prefer `POSTGRES_USER`
- `DB_PASS` -> prefer `POSTGRES_PASSWORD`

Variables to remove or document as unused:
- `KEYCLOAK_URL`
- `REDIS_URL`
- `KAFKA_BOOTSTRAP_SERVERS` in `.env` for full Docker, because compose overrides app containers to `kafka:9092`

Variables missing:
- `FOS_SECURITY_ENABLED` from root `.env` if local full compose wants explicit auth/no-auth switching.
- `MONGODB_URI` from `.env`; compose injects it directly.
- `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` as canonical Spring variable names.
- `CORS_ALLOWED_ORIGINS` if LAN/frontend origins should be configurable.

## 6. Frontend Problems
- `fos-workspace-frontend/src/environments/environment.ts` uses localhost URLs even for production builds.
- `fos-workspace-frontend/src/app/features/workspace-calendar/workspace-calendar-api.service.ts` hardcodes `teamRefId = '00000000-0000-0000-0000-000000000001'`.
- `fos-workspace-frontend/src/app/features/workspace-calendar/workspace-calendar.component.ts` hardcodes actor/person UUIDs and a temporary no-auth role fallback.
- `fos-workspace-frontend/src/app/core/data/workspace-data.service.ts` is a large mock data service still used by players, player profile, notifications, inbox, workspace rail, and fallback document/profile flows.
- `fos-workspace-frontend/src/app/features/calendar/` contains an older local/mock calendar page not referenced by `app.routes.ts`; current route uses `features/workspace-calendar`.
- `fos-workspace-frontend/src/app/shared/onlyoffice-editor/` is a placeholder editor component not referenced by current routes; current route uses `features/workspace-onlyoffice`.
- `fos-workspace-frontend/frontend-plan/` contains design plans, screenshots, and notes. It is documentation/planning material, not needed for minimal local runtime.
- Angular build succeeds, but the initial bundle exceeds the configured budget by 145.10 kB and `landing-page.component.scss` exceeds its component style budget by 356 bytes.

## 7. Backend Problems
- `fos-sdk/sdk-security/FosSecurityContext` only reads top-level `roles`, which is fragile with Keycloak.
- `fos-sdk/sdk-security/FosJwtConverter` exists but is not wired into gateway/workspace/governance security configuration.
- `DocumentService`, `EventService`, and `OnlyOfficeConfigService` all use fallback actor/club UUID `00000000-0000-0000-0000-000000000001` when security is disabled. This is acceptable for no-auth dev but must not leak into real data assumptions.
- `DocumentService` emits `saved.getOwnerRef().toString()` as `actorRef` for document uploads. `WorkspaceKafkaConsumer` treats that as uploader actor ID, so notifications can be assigned to the club UUID instead of the real uploader when security is enabled.
- `OnlyOfficeSaveHandler` looks up callback documents with `findByResourceId(documentId)` and no tenant/security check. This relies entirely on callback authenticity.
- `OnlyOfficeConfigService` supports only `docx`, `xlsx`, `pptx`, and `pdf` based on content type, while the frontend advertises broader OnlyOffice extensions such as `doc`, `xls`, `ppt`, `txt`, `odt`, `ods`, `odp`.
- Host-run scripts `run-gateway.sh`, `run-governance.sh`, and `run-workspace.sh` use `mvn -pl <module> spring-boot:run` without `-am`; on a clean local Maven repo, app modules may fail unless SDK modules were installed/built first.

## 8. Docker and Infrastructure Problems
- `docker-compose.yml` includes OpenSearch, but no application config uses OpenSearch. It increases local memory cost and is not required for minimal local work.
- `docker-compose.yml` has no OPA service even though governance needs `OPA_URL`.
- `docker-compose.infra.yml` includes `fos-opa-mock`, but full compose does not.
- `docker-compose.noauth.yml` disables gateway/workspace security but intentionally leaves governance security enabled. Workspace-to-governance calls may still fail if governance requires JWT and no Authorization header exists.
- Keycloak and governance share the same Postgres database. This can work, but it couples Keycloak tables, Flyway history, and governance schemas in one DB.
- `start.ps1` runs `docker-compose down`, `build --no-cache`, and `up -d`; it is heavy and can stop running services. It is not destructive to volumes, but it is not a minimal safe start script.
- `.dockerignore` excludes `.env`, `.env.*`, and Markdown files from build context. This is good for secrets, but means Docker image builds cannot rely on env/doc files being copied.

## 9. ONLYOFFICE Integration Problems
- Full local `.env` uses a JWT secret shorter than the backend accepts.
- Full Docker callback URL resolves to `http://host.docker.internal:8080` instead of internal `http://fos-gateway:8080`.
- Callback endpoint is public and unsigned callback payloads are accepted even when JWT is enabled.
- `ONLYOFFICE_PUBLIC_URL=http://localhost:8084` is fine for same-machine browser testing but not for LAN/mobile testing; `HOST_LAN_IP` must be changed.
- `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000` is designed to be reachable by browser and OnlyOffice. This is plausible in Docker, but it depends on `host.docker.internal` support and exposed host ports.
- Frontend supports more file extensions than backend OnlyOffice config generation supports.

## 10. File Importance / Cleanup List
| File/Folder | Current Role | Needed for minimal local work? | Problem | Recommendation | Delete risk |
|---|---|---|---|---|---|
| `fos-workspace-frontend/frontend-plan/` | Design/planning archive | No | Not runtime; includes screenshots/text plans | Keep as docs or move outside runtime tree | safe to ignore |
| `AI_CONTEXT/` | Agent progress/checkpoint docs | No | Not runtime; useful only for AI workflow history | Keep if still used by agents; otherwise archive | risky to delete |
| `diagrams/class-diagram.md` | Documentation | No | Not runtime | Keep as documentation | safe to ignore |
| `fos-workspace-frontend/src/app/features/calendar/` | Older mock/local calendar implementation | No, current route uses `workspace-calendar` | Not referenced by `app.routes.ts` | Confirm with human, then delete later if obsolete | risky to delete |
| `fos-workspace-frontend/src/app/shared/onlyoffice-editor/` | Placeholder editor component | No | Not referenced by current routes; text says connect later | Confirm unused, then delete later | risky to delete |
| `fos-workspace-frontend/src/app/core/data/workspace-data.service.ts` | Mock/fallback data source | Partly, because several routed pages still depend on it | Demo data mixed with live API pages | Replace gradually with backend APIs; do not delete now | risky to delete |
| `docker-compose.noauth.yml` | Optional auth-bypass overlay | Optional | Governance remains secured; name may overpromise full no-auth | Keep but document limitation | safe to ignore |
| `docker-compose.infra.yml` | Hybrid infra stack | Optional for full Docker; useful for host-run dev | Separate env behavior from `.env` | Keep | safe to ignore |
| `opa-mock/` | Mock OPA response for hybrid/noauth | Optional | Allows everything; not real policy | Keep for no-auth development only | safe to ignore |
| `start.ps1` | Full stack rebuild/start helper | Optional | Stops stack and rebuilds no-cache every time | Replace with safer documented script later | risky to delete |
| `.env.dev` | Ignored local hybrid env | Optional | README depends on ignored file existing | Rename template to `.env.dev.example`; keep actual ignored | unknown, needs human confirmation |
| `fos-workspace-frontend/dist/` | Generated Angular build output | No | Generated by verification build and ignored | Do not commit | safe to delete later |

## 11. Placeholder / Demo / Fake Values
| Value | File | Why suspicious | Should replace with |
|---|---|---|---|
| `change-me-onlyoffice-secret` | `.env`, `.env.example` | Placeholder and too short for backend JWT signing | 32+ byte local secret |
| `change-me-admin-user` | `.env.example` | Placeholder admin user | Local-only documented value |
| `change-me-keycloak-password` | `.env.example` | Placeholder secret | Local-only documented value |
| `change-me-webhook` | `.env.example` | Placeholder webhook secret | Local-only documented value |
| `Fos!Keycloak#2026` | `.env` | Real-looking local secret | Keep only in ignored `.env`, not templates |
| `FosWebhookSecret-2026-Local` | `.env` | Real-looking local secret | Keep only in ignored `.env`, not templates |
| `Fos!OpenSearch#2026` | `.env` | Real-looking local secret | Keep only in ignored `.env`, not templates |
| `00000000-0000-0000-0000-000000000001` | workspace services and frontend | Fallback club/team/actor ID | Real current user/team/club context from auth/canonical data |
| `11111111-1111-1111-1111-111111111101` | frontend calendar/profile | Hardcoded actor/person ID | Current actor from token/backend profile |
| `noop.fos.local` | `NoopStorageAdapter`, frontend checks | Deliberate no-op storage URL | Keep only for dev/test no-op storage |
| Mock people/images/events | `WorkspaceDataService`, `workspace-calendar.component.ts` | Demo data mixed with app flows | Backend data or seeded local fixtures |

## 12. Mismatches Found
- Keycloak mismatch: frontend/backend expect realm `fos`, but compose does not import/create it.
- Keycloak role mismatch: frontend supports nested Keycloak roles; backend expects top-level `roles`.
- OPA mismatch: full compose config uses OPA URL, but full compose does not run OPA.
- OnlyOffice secret mismatch: `.env.dev` has a long valid secret; `.env` and `.env.example` have a short invalid secret.
- OnlyOffice callback mismatch: compose default suggests gateway service URL, but `.env` overrides to host URL.
- Frontend/backend file type mismatch: frontend advertises many OnlyOffice-supported extensions; backend config only accepts content types resolving to `docx`, `xlsx`, `pptx`, `pdf`.
- Env naming mismatch: `.env` uses `DB_USER`/`DB_PASS`; Spring configs expect `POSTGRES_USER`/`POSTGRES_PASSWORD`.
- No-auth mismatch: `docker-compose.noauth.yml` disables gateway/workspace security but not governance security.
- Runtime data mismatch: frontend players/notifications/profile still use local mock services while documents/calendar partially use backend APIs.
- Native script mismatch: README tells `mvn -pl <module> -am ...` in places, but run scripts omit `-am`.

## 13. Verification Results
| Command | Result | Notes |
|---|---|---|
| `git status --short` | Success | Dirty files are inside ignored `report/` scope; left untouched. |
| `mvn compile -DskipTests` | Failed in sandbox | Maven could not download Spring Boot parent because network was sandbox-blocked. |
| `mvn compile -DskipTests` with approved dependency access | Success | All Java modules compiled successfully. |
| `npm run build` in `fos-workspace-frontend` | Success with warnings | Initial bundle budget exceeded by 145.10 kB; landing page SCSS budget exceeded by 356 bytes. |
| `docker compose config` | Success with Docker config warnings | Resolved full compose; showed short OnlyOffice secret and missing OPA service problem. Docker warned about access to `C:\Users\DELL\.docker\config.json`. |
| `docker compose --env-file .env.dev -f docker-compose.infra.yml config` | Success with Docker config warnings | Resolved infra stack; uses long OnlyOffice secret and includes `fos-opa-mock`. |
| `docker compose ps` | Success after Docker API approval | No compose services are currently running. |

## 14. Recommended Fix Order
1. Fix env source of truth: make `.env.example` valid, keep `.env` ignored, and create a clear `.env.dev.example` if hybrid dev remains supported.
2. Fix Docker/service URLs: add OPA to full compose, use correct OnlyOffice callback URL per mode, and remove/optionalize OpenSearch for minimal local setup.
3. Fix auth/Keycloak claims: add realm/client import and role/club claim mappers, or update backend role extraction for standard Keycloak claims.
4. Fix gateway/workspace/governance security flow: verify no-auth overlay behavior against governance and policy calls.
5. Fix storage/MinIO: confirm `MINIO_PUBLIC_ENDPOINT` is reachable from browser and OnlyOffice, and keep bucket creation through `MinioStorageAdapter`.
6. Fix ONLYOFFICE: enforce signed callbacks, align JWT secret, and align frontend file type support with backend.
7. Clean unused files: after human confirmation, remove obsolete frontend mock components/plans that are no longer part of runtime.

## 15. Do Not Touch / Keep
- `report/`, `reports/`, `rapport/`, `rapports/`: intentionally ignored and should not be modified by this audit.
- `pom.xml`, `fos-sdk/**/pom.xml`, service POM files: important build files.
- `docker-compose.yml`, `docker-compose.infra.yml`, `docker-compose.noauth.yml`: important local infrastructure files.
- `fos-governance-service/src/main/resources/db/migration/`: required Flyway migrations.
- `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/`: required Mongock migrations.
- `fos-governance-service/src/main/resources/opa/`: real policy source, even though it is not mounted into full compose yet.
- `opa-mock/`: useful for hybrid/no-auth local policy mocking.
- `.env`: keep local and ignored; do not commit.
- `.env.example`: keep tracked as the template after fixing invalid placeholder values.
- `fos-workspace-frontend/package-lock.json`: important reproducible frontend build file.
