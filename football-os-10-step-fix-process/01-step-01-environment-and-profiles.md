# Step 01 — Environment And Profiles

## 1. Purpose
Fix committed environment templates and runtime profile configuration so local development modes are safe, explicit, and reproducible. This step exists to prevent weak placeholders, env naming drift, hardcoded CORS, and frontend default URL mistakes while keeping local `.env` and `.env.dev` user-owned.

## 2. Errors Covered
- `.env.example` contains or may contain weak placeholders, including a short OnlyOffice JWT secret.
- `.env.dev.example` may be absent while README or tooling expects `.env.dev`.
- `DB_USER`/`DB_PASS` and `POSTGRES_USER`/`POSTGRES_PASSWORD` naming may be inconsistent.
- Gateway CORS is hardcoded to local origins instead of env-configurable values.
- Frontend environment defaults may point production/runtime builds at local-only URLs.
- Real `.env` and `.env.dev` contain user-owned local values and must not be edited automatically.

## 3. Files To Inspect
- `.env.example`
- `.env.dev.example`
- `.env`
- `.env.dev`
- `.gitignore`
- `README.md`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `docker-compose.noauth.yml`
- `fos-gateway/src/main/resources/application.yml`
- `fos-gateway/src/main/resources/application-dev.yml`
- `fos-gateway/src/main/resources/application-staging.yml`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-workspace-frontend/src/environments/environment.ts`
- `fos-workspace-frontend/src/environments/environment.development.ts`

## 4. Files Allowed To Modify
- `.env.example`
- `.env.dev.example`
- `README.md`
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `docker-compose.noauth.yml`
- `fos-gateway/src/main/resources/application.yml`
- `fos-gateway/src/main/resources/application-dev.yml`
- `fos-gateway/src/main/resources/application-staging.yml`
- `fos-workspace-service/src/main/resources/application.yml`
- `fos-workspace-service/src/main/resources/application-dev.yml`
- `fos-workspace-frontend/src/environments/environment.ts`
- `fos-workspace-frontend/src/environments/environment.development.ts`

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
- Any Java, TypeScript, or Docker file not listed in section 4.

## 6. Automatic Fixes To Perform
1. Fix committed env placeholders.
   - Objective: Make `.env.example` safe and structurally valid.
   - Exact implementation instruction: Replace any short `ONLYOFFICE_JWT_SECRET` placeholder with `local-onlyoffice-jwt-secret-32bytes-minimum`. Replace weak `change-me-*` placeholders with clearly fake local placeholders that document the variable without pretending to be real secrets.
   - Safety rule: Do not copy values from `.env` or `.env.dev`.
   - Verification command: `Select-String -Path .env.example -Pattern "ONLYOFFICE_JWT_SECRET|change-me-onlyoffice-secret"`
   - Expected result: `ONLYOFFICE_JWT_SECRET` is present with a 32+ character placeholder and `change-me-onlyoffice-secret` is absent.
   - Stop condition if it fails: Stop if the only available value is in `.env` or `.env.dev`; record manual blocker `MANUAL-004`.

2. Create or repair the hybrid env template.
   - Objective: Make host-backend or hybrid Docker development reproducible without tracking real `.env.dev`.
   - Exact implementation instruction: If `.env.dev.example` does not exist, create it using non-secret development defaults and placeholders only. Include storage, database, Keycloak, CORS, MinIO, and OnlyOffice variables needed by `docker-compose.infra.yml` and host-run services.
   - Safety rule: Do not unignore or edit `.env.dev`.
   - Verification command: `git check-ignore -v .env .env.dev; Test-Path .env.dev.example`
   - Expected result: `.env` and `.env.dev` remain ignored, and `.env.dev.example` exists.
   - Stop condition if it fails: Stop if `.gitignore` would need to expose real env files.

3. Normalize Postgres env naming.
   - Objective: Remove confusion between `DB_USER`/`DB_PASS` and `POSTGRES_USER`/`POSTGRES_PASSWORD`.
   - Exact implementation instruction: Use `POSTGRES_USER` and `POSTGRES_PASSWORD` as canonical names in committed templates and compose interpolation. If backward compatibility is needed, add only a simple Compose fallback that `docker compose config` proves valid.
   - Safety rule: Do not rename databases, volumes, or persisted data.
   - Verification command: `docker compose config`
   - Expected result: Compose resolves without missing variable or interpolation errors.
   - Stop condition if it fails: Remove invalid fallback syntax, keep the canonical variables only, document the migration, and rerun config.

4. Make gateway CORS env-configurable.
   - Objective: Allow local frontend and LAN origins without source edits.
   - Exact implementation instruction: Replace hardcoded local CORS origins with `${CORS_ALLOWED_ORIGINS:http://localhost:4200}` or, if the gateway already uses origin patterns, `${CORS_ALLOWED_ORIGIN_PATTERNS:http://localhost:4200}`. Add the selected variable to `.env.example` and `.env.dev.example`.
   - Safety rule: Do not use `*` with credentials enabled.
   - Verification command: `mvn -pl fos-gateway -am -DskipTests compile`
   - Expected result: Gateway compiles and default CORS remains restricted to local frontend.
   - Stop condition if it fails: Revert the CORS binding change, document the limitation in README, and stop this task.

5. Align frontend environment defaults.
   - Objective: Ensure frontend runtime defaults use gateway and do not hide direct service URLs.
   - Exact implementation instruction: Keep local development `gatewayBaseUrl` on `http://localhost:8080` and Keycloak on `http://localhost:8180`. Remove any runtime direct workspace `8082` default from environment files. If production runtime config is not implemented, document that production deployment requires explicit environment replacement instead of inventing a new runtime config system.
   - Safety rule: Do not redesign frontend configuration.
   - Verification command: `cd fos-workspace-frontend; npm run build`
   - Expected result: Angular build succeeds.
   - Stop condition if it fails: Revert the environment change and record the build error for Step 07.

## 7. Manual-Only Blockers
- `MANUAL-004`: User must set real local `.env` secrets, including a 32+ character `ONLYOFFICE_JWT_SECRET`.
- `MANUAL-005`: User must provide `HOST_LAN_IP` for LAN/mobile testing.

## 8. Verification Commands
- `Select-String -Path .env.example -Pattern "ONLYOFFICE_JWT_SECRET|change-me-onlyoffice-secret"`
- `git check-ignore -v .env .env.dev`
- `Test-Path .env.dev.example`
- `docker compose config`
- `docker compose --env-file .env.dev -f docker-compose.infra.yml config`
- `mvn -pl fos-gateway -am -DskipTests compile`
- `cd fos-workspace-frontend; npm run build`

## 9. Acceptance Criteria
- [ ] `.env.example` contains no short OnlyOffice JWT placeholder.
- [ ] `.env.dev.example` exists and contains only non-secret local defaults/placeholders.
- [ ] `.env` and `.env.dev` remain ignored and unmodified.
- [ ] Postgres env names are canonical or clearly documented.
- [ ] CORS is env-configurable and does not use wildcard credentials.
- [ ] Frontend defaults call the gateway, not workspace directly.

## 10. Documentation To Update
Update `README.md` with local env setup, `.env.example` to `.env`, `.env.dev.example` to `.env.dev`, canonical database variable names, CORS env variables, and the rule that real local env files are user-owned.

## 11. Rollback Plan
Revert only the files listed in section 4 to their previous Git versions. Delete `.env.dev.example` only if this step created it and rollback is requested. Do not touch `.env`, `.env.dev`, databases, Docker volumes, or generated folders.
