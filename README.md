# Football OS Core

## Project Purpose

Football OS Core is a multi-module monorepo that contains the backend platform for Football OS and its workspace frontend.

It includes:

- shared SDK libraries (`fos-sdk`)
- governance service (`fos-governance-service`)
- workspace service (`fos-workspace-service`)
- API gateway (`fos-gateway`)
- Angular workspace frontend (`fos-workspace-frontend`)
- local infrastructure orchestration (`docker-compose.yml`)

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

1. Create a local env file from `.env.example`.
2. Start infrastructure:

```bash
docker compose up -d
```

3. Build backend modules:

```bash
mvn clean install -DskipTests
```

4. Install frontend dependencies:

```bash
cd fos-workspace-frontend
npm ci
```

## Running the Project

From repo root, run backend services in this order:

```bash
mvn -pl fos-governance-service -am spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
mvn -pl fos-workspace-service -am spring-boot:run
mvn -pl fos-gateway -am spring-boot:run
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
