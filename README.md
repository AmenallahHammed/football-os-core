# Football OS Core

## Project Purpose

Football OS Core is a multi-module monorepo that contains the backend platform for Football OS and its workspace frontend.

It includes:

- shared SDK libraries (`fos-sdk`)
- governance service (`fos-governance-service`)
- workspace service (`fos-workspace-service`)
- API gateway (`fos-gateway`)
- Angular workspace frontend (`fos-workspace-frontend`)
- local stack orchestration (`docker-compose.yml`, `docker-compose.infra.yml`)

## Technologies Used

- Java 21, Spring Boot 3.3.x, Maven 3.9+
- PostgreSQL, MongoDB, Kafka, Redis, MinIO
- Keycloak (OIDC/JWT), OPA, OnlyOffice
- Angular 17, TypeScript, Node/NPM
- Docker Compose

## Repository Structure

```text
.
|- pom.xml
|- docker-compose.yml
|- docker-compose.infra.yml
|- start.ps1
|- run-dev.sh
|- fos-sdk/
|- fos-governance-service/
|- fos-gateway/
|- fos-workspace-service/
|- fos-workspace-frontend/
|- report/
|  '- implementation-summary.md
|- diagrams/
|  '- class-diagram.md
'- AI_CONTEXT/  (retained internal context files)
```

Module-level documentation:

- `fos-sdk/README.md`
- `fos-governance-service/README.md`
- `fos-workspace-service/README.md`
- `fos-gateway/README.md`
- `fos-workspace-frontend/README.md`

## Installation

1. Choose one local setup.

Full Docker stack:

- Create a local `.env` file from `.env.example` before starting the stack.
- Keep local `.env` user-owned and uncommitted.
- Canonical database variable names are `POSTGRES_USER` and `POSTGRES_PASSWORD`.
- Gateway CORS origins are configured through `CORS_ALLOWED_ORIGINS` (default `http://localhost:4200`).

```bash
docker compose up -d --build
```

PowerShell helper:

```powershell
.\start.ps1
```

Hybrid local dev, lower RAM usage:

- Copy `.env.dev.example` to `.env.dev` and adjust local-only values as needed.
- Keep `.env.dev` user-owned and uncommitted.
- `docker-compose.infra.yml` starts only infrastructure services.
- `run-dev.sh` loads `.env.dev`, starts infra, and prints the native Maven commands for the three app services.
- `opensearch` stays disabled in this mode to reduce memory use. OnlyOffice is enabled for local document editor testing.

```bash
./run-dev.sh
```

If you prefer to start infra manually:

```bash
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d
```

Leanest day-to-day backend subset:

```bash
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d postgres mongodb zookeeper kafka redis fos-opa-mock
```

2. If you are not using Docker Compose for the backend apps, build backend modules:

```bash
mvn clean install -DskipTests
```

3. Install frontend dependencies:

```bash
cd fos-workspace-frontend
npm ci
```

## Running Services Locally

- `docker-compose.yml` starts the full stack, including `fos-governance-service`, `fos-workspace-service`, and `fos-gateway`.
- Full Docker mode runs a real OPA container (`fos-opa`) and governance uses `OPA_URL=http://opa:8181`.
- `docker-compose.infra.yml` is the hybrid-dev stack: infrastructure stays in Docker while the three app services run natively on the host.
- `docker-compose.noauth.yml` is a Docker-only auth-bypass overlay for local testing; it sets `FOS_SECURITY_ENABLED=false` for gateway, workspace, and governance and routes governance policy calls to `fos-opa-mock`.
- `run-dev.sh` is the quickest low-RAM entry point for hybrid dev.
- Lowest practical hybrid subset for normal backend work: `postgres`, `mongodb`, `zookeeper`, `kafka`, `redis`, `fos-opa-mock`.
- Add `minio` only when testing real object storage.
- Add `keycloak` only when testing real auth.
- `opensearch` is disabled in `docker-compose.infra.yml` to reduce local RAM usage.
- OpenSearch default behavior in full compose is intentionally unchanged pending an explicit `MANUAL-009` decision.
- OnlyOffice is exposed on `8084`; set `HOST_LAN_IP` and `ONLYOFFICE_PUBLIC_URL` in `.env` before testing documents from the frontend.
- Do not run a native app service on the same port as a Docker container from the full stack.
- Do not use `docker compose down -v` during this process.
- Document upload signals now include `uploaderActorId`; notification consumers read it first and fall back to legacy `actorRef` for backward compatibility.

Storage mode notes:

- `STORAGE_PROVIDER=noop`: metadata-only local development. No real object upload/download.
- `STORAGE_PROVIDER=minio`: real object upload/download and OnlyOffice editing.
- Full Docker mode uses backend-internal `MINIO_ENDPOINT=http://minio:9000`.
- Browser and OnlyOffice-facing URLs should use `MINIO_PUBLIC_ENDPOINT=http://host.docker.internal:9000` in same-machine Docker testing.
- Hybrid host-backend mode can use `MINIO_ENDPOINT=http://localhost:9000` and `MINIO_PUBLIC_ENDPOINT=http://localhost:9000`.
- Bucket defaults: full Docker `MINIO_BUCKET=fos-workspace`; hybrid dev template `MINIO_BUCKET=fos-workspace-dev`.
- For LAN/device testing, set `HOST_LAN_IP` and update public endpoints manually (`MANUAL-005`).

Real document smoke prerequisites:

- Confirm a real MinIO bucket and object exist (for example in MinIO Console on `http://localhost:9001`).
- Provide a real backend document UUID with a current uploaded version (`MANUAL-008`).
- Provide the real team UUID and actor/user expected to access the document and related flows (`MANUAL-010`).

Why `mvn spring-boot:run` fails at repo root:

- The root `pom.xml` is an aggregator POM (`packaging: pom`) and does not contain an application `main` class.
- Maven executes goals through the reactor, so a root command is considered for selected modules, not just one app.
- This monorepo has multiple Spring Boot apps, so root `spring-boot:run` is ambiguous and can fail on non-app modules.

Correct Maven pattern for one app at a time:

```bash
mvn -pl <module-name> spring-boot:run
```

- `-pl` means "project list" (run only the module you name).
- `-am` means "also make" (build required sibling modules too). Use `-am` for build goals such as `install` when needed, not for root `spring-boot:run` in this repo.

Use the root helper scripts for host-based single-service development:

```bash
./run-governance.sh
./run-workspace.sh
./run-gateway.sh
```

These scripts run Maven with `-pl <module> -am spring-boot:run` so required sibling modules are built automatically on clean machines.

Use the hybrid helper for all three native app services with shared Docker infra:

```bash
./run-dev.sh
```

Use the Makefile shortcuts:

- A `Makefile` is a small command map for the team: everyone uses the same short commands instead of remembering long Maven flags.
- This reduces onboarding friction and avoids "works on my machine" differences in startup commands.

```bash
make help
make governance
make workspace
make gateway
make all
make stop
```

Notes:

- `make all` starts all three services in background and writes logs/PIDs to `.run/`.
- `make stop` stops all locally running Spring Boot service processes.
- Scripts do not auto-source `.env` by default. If required, export env vars first:

```bash
set -a; source .env; set +a
make gateway
```

Windows note:

- Git Bash or WSL: use the same `.sh` scripts and `make` commands.
- PowerShell/CMD: start infra directly, then run Maven commands from repo root:

```powershell
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d
```

```powershell
mvn "-Dspring-boot.run.profiles=dev" -pl fos-governance-service spring-boot:run
mvn "-Dspring-boot.run.profiles=dev" -pl fos-workspace-service spring-boot:run
mvn "-Dspring-boot.run.profiles=dev" -pl fos-gateway spring-boot:run
```

If you want the leanest subset instead of the full infra file:

```powershell
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d postgres mongodb zookeeper kafka redis fos-opa-mock
```

Run frontend:

```bash
cd fos-workspace-frontend
npm start
```

Default local ports:

- gateway: `8080`
- governance: `8081`
- workspace: `8082`
- frontend: `4200`
- OnlyOffice Document Server: `8084`

OnlyOffice local connectivity quick check:

```bash
docker compose config
docker compose up -d
curl -I http://localhost:8084/web-apps/apps/api/documents/api.js
curl -I http://localhost:8080/actuator/health
```

Notes:

- The frontend must call gateway APIs on `http://localhost:8080` (not workspace `8082` directly).
- Set `CORS_ALLOWED_ORIGINS` when your frontend origin differs from `http://localhost:4200`.
- `HOST_LAN_IP` defaults to `localhost` for same-machine testing. For another device on LAN, set it to your machine real IP.
- If the editor opens but cannot save, verify `ONLYOFFICE_CALLBACK_BASE_URL` is reachable from inside the `fos-onlyoffice` container.
- If the editor opens but cannot load the file, inspect `document.url` in `/api/v1/onlyoffice/config` response and confirm MinIO URL reachability from both browser and ONLYOFFICE.
- Committed OnlyOffice JWT placeholders are local examples only; keep real `.env` values user-owned and ensure `ONLYOFFICE_JWT_SECRET` is at least 32 characters.
- Full Docker callback base URL should be `http://fos-gateway:8080`; hybrid host-backend mode should use `http://host.docker.internal:8080`.
- Only these OnlyOffice formats are supported for editor flows: `docx`, `xlsx`, `pptx`, `pdf`.
- Security allow-list is intentionally narrow: `/api/v1/onlyoffice/callback/**` and `/api/v1/onlyoffice/health` may be public; `/api/v1/onlyoffice/config` remains protected when security is enabled.

Keycloak local auth notes:

- Full Docker and hybrid infra both import `keycloak/fos-realm.local.json` with `start-dev --import-realm`.
- Realm: `fos`
- Public client: `fos-workspace-frontend`
- Redirect URI: `http://localhost:4200/*`
- Web origin: `http://localhost:4200`
- Local realm roles include `ROLE_HEAD_COACH`, `ROLE_CLUB_ADMIN`, `ROLE_MEDICAL_STAFF`, and `ROLE_ANALYST`.
- `fos_club_id` is mapped from the user attribute `fos_club_id`. Real club/team/actor IDs are manual local data (`MANUAL-003`).
- If mapper or token parsing changes were applied, log out, clear site data for `localhost:4200` and `localhost:8180`, then log in again to refresh token claims.

OnlyOffice smoke flow (real data required):

1. Confirm `api.js` is reachable at `http://localhost:8084/web-apps/apps/api/documents/api.js`.
2. Request OnlyOffice config through gateway (`/api/v1/onlyoffice/config`) using a real document UUID.
3. Open the editor in frontend and make a save.
4. Verify backend recorded a new version or returned a successful save response.
5. If this fails, capture response body, status code, and the document UUID used (`MANUAL-008`, `MANUAL-010`).

Frontend runtime cleanup notes:

- Runtime frontend API calls must go through gateway (`http://localhost:8080`), not workspace port `8082`.
- Dev-only fallback IDs are defined in `environment.development.ts` (`devFallbackTeamId`, `devFallbackClubId`, `devFallbackActorId`) and are not production data.
- `WorkspaceDataService` remains a local fallback source for pages that do not yet have complete backend APIs; components should prefer gateway APIs when available.
- Active calendar/editor routes use `features/workspace-calendar` and `features/workspace-onlyoffice`.
- Legacy folders `features/calendar` and `shared/onlyoffice-editor` are currently classified as inactive references and are kept in place pending explicit `MANUAL-007` approval.
- Current Angular production build warnings are accepted for now: initial bundle budget and `landing-page.component.scss` component-style budget.

Backend cleanup notes:

- Backend fallback UUID constants are explicitly marked as local no-auth development fallback values only.
- Workspace permission checks must continue through `PolicyClient` (no direct OPA client in workspace modules).
- Workspace storage operations must continue through `StoragePort` (no direct storage SDK client usage in workspace modules).
- Kafka consumers must keep using `AbstractFosConsumer`.

## Testing and Build

Backend:

```bash
mvn test
```

Frontend:

```bash
cd fos-workspace-frontend
npm run build
npm test
```

Note: some governance integration tests require local infrastructure (notably PostgreSQL/Kafka) to be running.

## Report and Diagrams

- implementation report: `report/implementation-summary.md`
- architecture/class diagram: `diagrams/class-diagram.md`
