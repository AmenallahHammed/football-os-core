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

```bash
docker compose up -d --build
```

PowerShell helper:

```powershell
.\start.ps1
```

Hybrid local dev, lower RAM usage:

- Ensure a local `.env.dev` file exists with the dev-only defaults needed by `keycloak` and `fos-opa-mock`.
- `docker-compose.infra.yml` starts only infrastructure services.
- `run-dev.sh` loads `.env.dev`, starts infra, and prints the native Maven commands for the three app services.
- `opensearch` and `onlyoffice` stay disabled in this mode to reduce memory use.

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
- `docker-compose.infra.yml` is the hybrid-dev stack: infrastructure stays in Docker while the three app services run natively on the host.
- `docker-compose.noauth.yml` remains the Docker-only auth-bypass overlay. It is not needed for the hybrid flow because the app services already default to the `dev` profile.
- `run-dev.sh` is the quickest low-RAM entry point for hybrid dev.
- Lowest practical hybrid subset for normal backend work: `postgres`, `mongodb`, `zookeeper`, `kafka`, `redis`, `fos-opa-mock`.
- Add `minio` only when testing real object storage.
- Add `keycloak` only when testing real auth.
- `opensearch` and `onlyoffice` are disabled in `docker-compose.infra.yml` to reduce local RAM usage.
- Do not run a native app service on the same port as a Docker container from the full stack.

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
