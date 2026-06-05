# Football OS Core

## Project Purpose

Football OS Core is a multi-module monorepo that contains the backend platform for Football OS and its workspace frontend.

It includes:

- shared SDK libraries (`fos-sdk`)
- governance service (`fos-governance-service`)
- workspace service (`fos-workspace-service`)
- API gateway (`fos-gateway`)
- Angular workspace frontend (`fos-workspace-frontend`)
- local stack orchestration (`docker-compose.yml`)

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
|- LOCAL_RUN.md
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

1. Create a local `.env` file from `.env.example`.
2. Use the Monday-demo local runtime in [LOCAL_RUN.md](LOCAL_RUN.md).
3. Start infrastructure only:

```bash
docker compose up -d
```

4. Build backend modules:

```bash
mvn clean install -DskipTests
```

5. Install frontend dependencies:

```bash
cd fos-workspace-frontend
npm ci
```

## Running Services Locally

- `docker-compose.yml` is the single active local stack and starts infrastructure containers only.
- Backend services run locally through Maven from the repository root.
- The frontend runs locally from `fos-workspace-frontend`.
- Security stays enabled by default. There is no supported no-auth local path for the Monday demo.
- Legacy compose and env variants are archived under `docker/legacy-compose/` and `config/legacy-env/`.
- The exact startup, shutdown, URLs, troubleshooting steps, and verification flow now live in [LOCAL_RUN.md](LOCAL_RUN.md).

Quick commands:

```bash
docker compose up -d
mvn clean install -DskipTests
mvn -pl fos-governance-service -am spring-boot:run
mvn -pl fos-workspace-service -am spring-boot:run
mvn -pl fos-gateway -am spring-boot:run
cd fos-workspace-frontend && npm start
```

Core local URLs:

- Frontend: `http://localhost:4200`
- Gateway: `http://localhost:8080`
- Governance: `http://localhost:8081`
- Workspace: `http://localhost:8082`
- Keycloak: `http://localhost:8180`
- OPA: `http://localhost:8181/v1/data/fos/allow`
- MinIO console: `http://localhost:9001`
- OnlyOffice api.js: `http://localhost:8084/web-apps/apps/api/documents/api.js`

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
