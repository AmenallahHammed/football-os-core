# 01 — Env And Profiles

## 1. Purpose
Fix environment-file ownership, safe placeholders, Spring profile variable naming, CORS configurability, and frontend environment defaults without changing runtime logic.

## 2. Source Errors From erreurs.md
- ERR-003: OnlyOffice JWT secret is too short in full local env
- ERR-006: Full Docker callback URL is overridden to host path
- Section 4: Configuration and Environment Problems
- Section 5: `.env` File Analysis
- Section 11: Placeholder / Demo / Fake Values
- Section 12: Env naming mismatch, OnlyOffice secret mismatch, production Angular localhost issue

## 3. Classification
- Short `ONLYOFFICE_JWT_SECRET` in `.env.example`: Agent-fixable.
- Short `ONLYOFFICE_JWT_SECRET` in local `.env`: Manual-only; see `manual-fixes.md` MANUAL-004.
- `.env.dev` template confusion: Agent-fixable by adding `.env.dev.example`; actual `.env.dev` remains local.
- `DB_USER`/`DB_PASS` naming split: Agent-fixable.
- Unused `KEYCLOAK_URL` and `REDIS_URL`: Agent-fixable if removing from examples; manual if user keeps them locally.
- CORS hardcoded to `localhost:4200`: Agent-fixable.
- Production Angular localhost URLs: Agent-fixable.
- LAN IP selection: Manual-only; see MANUAL-005.

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
- `fos-workspace-frontend/src/environments/environment.ts`
- `fos-workspace-frontend/src/environments/environment.development.ts`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not edit local `.env` or `.env.dev` unless the user explicitly asks.
- Do not edit Java application logic in this plan.

## 6. Current Problem Summary
The committed env template contains invalid or confusing placeholder values. The ignored local `.env` currently controls full compose, but it contains real-looking secrets and a short OnlyOffice JWT secret. Spring and compose use mixed variable names. Gateway CORS and Angular production URLs are hardcoded to localhost.

## 7. Target State
Committed env templates are safe, valid, and clear. Full Docker, hybrid Docker, and Angular environments have distinct documented roles. CORS can be configured through env. Local secrets remain local and user-owned.

## 8. Step-by-Step Execution Plan
### Step 1: Fix committed env template placeholders
- Objective: Make `.env.example` safe and structurally valid.
- Files to inspect: `.env.example`, `erreurs.md`
- Files to modify: `.env.example`
- Exact change to make: Replace `ONLYOFFICE_JWT_SECRET=change-me-onlyoffice-secret` with `ONLYOFFICE_JWT_SECRET=local-onlyoffice-jwt-secret-32bytes-minimum`. Replace `KEYCLOAK_ADMIN=change-me-admin-user`, `KEYCLOAK_ADMIN_PASSWORD=change-me-keycloak-password`, and `KEYCLOAK_WEBHOOK_SECRET=change-me-webhook` with clearly fake local placeholders that still document required variables.
- Safety rule: Do not copy values from local `.env`.
- Verification command: `Select-String -Path .env.example -Pattern "change-me-onlyoffice-secret|ONLYOFFICE_JWT_SECRET"`
- Expected result: The short secret is gone and the replacement is visible.
- What to do if verification fails: Reopen `.env.example`, fix the exact line, and rerun the command.

### Step 2: Add a tracked hybrid env template
- Objective: Stop README from depending on an ignored `.env.dev` without a committed template.
- Files to inspect: `.env.dev`, `.env.example`, `.gitignore`, `README.md`
- Files to modify: `.env.dev.example`, `README.md`
- Exact change to make: Create `.env.dev.example` using non-secret development defaults from `.env.dev`, including a 32+ byte OnlyOffice secret placeholder. Update README to say copy `.env.dev.example` to `.env.dev` for hybrid dev.
- Safety rule: Do not unignore or commit actual `.env.dev`.
- Verification command: `git check-ignore -v .env.dev; Test-Path .env.dev.example`
- Expected result: `.env.dev` remains ignored and `.env.dev.example` exists.
- What to do if verification fails: Restore `.gitignore` behavior and recreate only the example file.

### Step 3: Normalize Postgres env names
- Objective: Reduce `DB_USER`/`DB_PASS` versus `POSTGRES_USER`/`POSTGRES_PASSWORD` confusion.
- Files to inspect: `.env.example`, `docker-compose.yml`, `docker-compose.infra.yml`, Spring application YAML files
- Files to modify: `.env.example`, `docker-compose.yml`, `README.md`
- Exact change to make: Prefer `POSTGRES_USER` and `POSTGRES_PASSWORD` in `.env.example` and compose interpolation. If backward compatibility is needed, use compose fallback `${POSTGRES_USER:-${DB_USER:-fos}}` only if Docker Compose supports the expression after testing; otherwise choose one canonical name and document migration.
- Safety rule: Do not change database names or volume names.
- Verification command: `docker compose config`
- Expected result: Governance service receives `POSTGRES_USER` and `POSTGRES_PASSWORD`; config resolves without interpolation errors.
- What to do if verification fails: Revert nested interpolation and use simple canonical variables.

### Step 4: Make gateway CORS configurable
- Objective: Allow local LAN/frontend origins without code edits.
- Files to inspect: `fos-gateway/src/main/resources/application-dev.yml`, `fos-gateway/src/main/resources/application-staging.yml`
- Files to modify: `fos-gateway/src/main/resources/application-dev.yml`, `fos-gateway/src/main/resources/application-staging.yml`, `.env.example`
- Exact change to make: Replace hardcoded `allowedOrigins` values with `${CORS_ALLOWED_ORIGINS:http://localhost:4200}` if Spring YAML binding supports comma-separated origins, or document and use `allowedOriginPatterns` with `${CORS_ALLOWED_ORIGIN_PATTERNS:http://localhost:4200}`.
- Safety rule: Do not allow `*` with credentials.
- Verification command: `mvn -pl fos-gateway -am -DskipTests compile`
- Expected result: Gateway compiles.
- What to do if verification fails: Revert to hardcoded origins and document CORS manual limitation.

### Step 5: Fix frontend environment deployment defaults
- Objective: Avoid production builds permanently targeting localhost.
- Files to inspect: `fos-workspace-frontend/src/environments/environment.ts`, `environment.development.ts`, `angular.json`
- Files to modify: `fos-workspace-frontend/src/environments/environment.ts`, `README.md`
- Exact change to make: Set production `gatewayBaseUrl` and `auth.keycloakUrl` to deployment-neutral values only if the project has a runtime config pattern; otherwise document that production build currently targets localhost and should not be used for deployment. Do not invent a runtime config system in this plan.
- Safety rule: Keep development environment at `http://localhost:8080` and `http://localhost:8180`.
- Verification command: `npm run build`
- Expected result: Angular build succeeds.
- What to do if verification fails: Revert environment changes and capture the build error.

## 9. Verification Commands
- `Select-String -Path .env.example -Pattern "ONLYOFFICE_JWT_SECRET|change-me-onlyoffice-secret"`
- `git check-ignore -v .env .env.dev`
- `docker compose config`
- `docker compose --env-file .env.dev -f docker-compose.infra.yml config`
- `mvn -pl fos-gateway -am -DskipTests compile`
- `npm run build`

## 10. Acceptance Criteria
- [ ] `.env.example` contains no short OnlyOffice JWT secret.
- [ ] `.env.dev.example` exists if hybrid dev still uses `.env.dev`.
- [ ] `.env` and `.env.dev` remain ignored.
- [ ] Compose config resolves canonical Postgres variables.
- [ ] CORS remains scoped and does not use wildcard credentials.
- [ ] Angular build succeeds.

## 11. Rollback Plan
Revert edits to `.env.example`, `.env.dev.example`, README, compose files, gateway YAML files, and Angular environment files using the previous Git version. Do not touch ignored local `.env` or `.env.dev`.

## 12. Notes For The Execution Agent
Coordinate with `06-onlyoffice-plan.md` before changing OnlyOffice URL variables. Do not treat local `.env` as committed truth. Keep user-owned secrets manual.
