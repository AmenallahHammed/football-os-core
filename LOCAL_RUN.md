# Local Monday Demo Run Guide

This is the single supported local runtime path for the Monday technical demo.

- One active env file: repository-root `.env`
- One active compose file: repository-root `docker-compose.yml`
- Security enabled by default
- Backend services run locally with Maven
- Frontend runs locally with npm
- Docker Compose runs infrastructure containers only

## Startup

Run every backend command from the repository root `D:\fos-sdk`.

1. Stop old containers:

```powershell
docker compose down
```

2. Optional full reset:

```powershell
docker compose down -v
```

3. Start infrastructure:

```powershell
docker compose up -d
```

4. Check containers:

```powershell
docker compose ps
```

5. Build backend:

```powershell
mvn clean install -DskipTests
```

6. Run governance:

```powershell
mvn -pl fos-governance-service -am -DskipTests install
mvn "-Dspring-boot.run.arguments=--spring.profiles.active=dev" -f fos-governance-service/pom.xml spring-boot:run
```

7. Run workspace:

```powershell
mvn -pl fos-workspace-service -am -DskipTests install
mvn "-Dspring-boot.run.arguments=--spring.profiles.active=dev" -f fos-workspace-service/pom.xml spring-boot:run
```

8. Run gateway:

```powershell
mvn -pl fos-gateway -am -DskipTests install
mvn "-Dspring-boot.run.arguments=--spring.profiles.active=dev" -f fos-gateway/pom.xml spring-boot:run

```
specifically the gateway can take a few seconds to bind to port 8080, so if you see a "port already in use" error, check for the old process and stop it:

```powershell
Stop-Process -Id 4960
9. Run frontend:

```powershell
cd fos-workspace-frontend
npm install
npm start
```

Use `npm ci` instead of `npm install` when you want a clean lockfile-based install.

## URLs

- Frontend: `http://localhost:4200`
- Gateway health: `http://localhost:8080/actuator/health`
- Governance health: `http://localhost:8081/actuator/health`
- Workspace health: `http://localhost:8082/actuator/health`
- Keycloak: `http://localhost:8180`
- OPA policy endpoint: `http://localhost:8181/v1/data/fos/allow`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- OnlyOffice: `http://localhost:8084`
- OnlyOffice API script: `http://localhost:8084/web-apps/apps/api/documents/api.js`

## Runtime Notes

- Normal browser API traffic must go through the gateway on `http://localhost:8080`.
- The frontend must never call `http://localhost:8081` or `http://localhost:8082` directly.
- OnlyOffice config is requested through the gateway, but the document download and callback URLs intentionally target the host-run workspace service through `http://host.docker.internal:8082`.
- The workspace service uses `MINIO_ENDPOINT=http://localhost:9000`.
- The governance service uses `OPA_URL=http://localhost:8181`.
- The local Spring Boot `dev` profiles import the repository-root `.env` even when a service is launched from its module directory, so you do not need a separate `.env.dev`.
- The helper scripts use `mvn ... install` before `spring-boot:run` so local SDK snapshot changes are visible to module-level runs.
- The helper scripts launch Spring Boot with the `dev` profile for readable console logs and local debug-friendly settings.
- MinIO bucket creation is handled by the application code on first use.

## Troubleshooting

### Port already in use

Check the conflicting listener:

```powershell
Get-NetTCPConnection -LocalPort 4200,5432,6379,8080,8081,8082,8084,8180,8181,9000,9001,9092,27017 -ErrorAction SilentlyContinue |
  Select-Object LocalPort, State, OwningProcess
```

Stop the old process or container, then rerun `docker compose down` and restart the affected service.

### Keycloak not ready

Keycloak can take longer than the other containers because it waits for PostgreSQL and imports the local realm.

```powershell
docker compose logs keycloak --tail 100
```

Wait until `http://localhost:8180` responds before signing in from the frontend.

### Kafka connection refused

Confirm both `zookeeper` and `kafka` are up:

```powershell
docker compose ps zookeeper kafka
```

If Kafka is unhealthy, restart it:

```powershell
docker compose restart kafka
```

### MinIO bucket missing

The workspace service creates the bucket automatically when it first writes to MinIO. If startup logs show MinIO auth or bucket errors, verify the root `.env` values for:

- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_BUCKET`
- `MINIO_ENDPOINT`

### OnlyOffice callback failed

For this local setup the callback base URL is `http://host.docker.internal:8082`.

If saves fail:

- Confirm the workspace service is running on port `8082`
- Confirm Docker Desktop resolves `host.docker.internal`
- Confirm `http://localhost:8084/healthcheck` responds
- Check workspace logs for `/api/v1/onlyoffice/callback/...` requests

### Changed Wi-Fi or local IP

This setup does not depend on your LAN IP. It uses `localhost` for browser-facing URLs and `host.docker.internal` for the container-to-host OnlyOffice callback path.

### Frontend accidentally calling 8082

Search the frontend tree:

```powershell
rg -n "localhost:8081|localhost:8082|:8081|:8082" fos-workspace-frontend/src
```

Runtime API calls should target gateway paths only.

### 401 or 403 because token claims are missing

Security is enabled. If requests fail after login:

- Sign out and sign back in through Keycloak
- Verify the token contains realm roles and `fos_club_id`
- Check gateway, governance, and workspace logs for claim parsing failures
- Confirm the imported local realm file still matches the frontend client configuration
