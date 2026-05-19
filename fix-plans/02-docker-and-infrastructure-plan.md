# 02 — Docker And Infrastructure

## 1. Purpose
Make the Docker Compose topology match the services required for minimal local work, especially OPA/policy, optional OpenSearch, and safer start behavior.

## 2. Source Errors From erreurs.md
- ERR-002: Full Compose references OPA but does not start OPA
- Section 8: Docker and Infrastructure Problems
- Section 10: `docker-compose.noauth.yml`, `docker-compose.infra.yml`, `opa-mock/`, `start.ps1`
- Section 12: OPA mismatch, no-auth mismatch
- Section 13: Docker verification notes

## 3. Classification
- Add OPA or mock service to full compose: Agent-fixable.
- Decide OpenSearch default behavior: Needs human confirmation; see MANUAL-009.
- Start Docker Desktop: Manual-only; see MANUAL-001.
- Safer `start.ps1` behavior: Agent-fixable after user approves behavior.
- Keycloak/Postgres DB sharing: Agent-fixable to document; changing DB topology is mixed and should not happen without confirmation.

## 4. Files Allowed To Modify
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `docker-compose.noauth.yml`
- `opa-mock/default.conf`
- `opa-mock/allow.json`
- `README.md`
- `start.ps1`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not edit Java policy logic in this plan.
- Do not remove volumes or issue `docker compose down -v`.

## 6. Current Problem Summary
Full compose starts governance with an OPA URL but no OPA service. Hybrid compose has `fos-opa-mock`, creating different behavior. OpenSearch starts in full compose even though no app uses it. The no-auth overlay disables only gateway/workspace security while governance remains secured.

## 7. Target State
Full local compose has a reachable policy endpoint. Optional services are clearly optional. No-auth behavior is explicit. Docker helper commands are safe and documented.

## 8. Step-by-Step Execution Plan
### Step 1: Add policy service to full compose
- Objective: Ensure governance can reach OPA during full Docker startup.
- Files to inspect: `docker-compose.yml`, `docker-compose.infra.yml`, `opa-mock/default.conf`, `fos-governance-service/src/main/resources/opa/*.rego`
- Files to modify: `docker-compose.yml`, `.env.example` if needed
- Exact change to make: Add either a real `opa` service mounting `fos-governance-service/src/main/resources/opa/` and exposing/internalizing port 8181, or add `fos-opa-mock` to full compose for local-only allow-all policy. Prefer real OPA if policy files are loadable; otherwise use `fos-opa-mock` and label it local-only.
- Safety rule: Do not alter Rego policy semantics in this plan.
- Verification command: `docker compose config`
- Expected result: Full compose includes a policy service and `OPA_URL` points to a service hostname, not `host.docker.internal`, unless explicitly documented for host-run mode.
- What to do if verification fails: Revert compose change and choose the simpler `fos-opa-mock` service.

### Step 2: Align no-auth overlay behavior
- Objective: Prevent workspace no-auth mode from calling secured governance without JWT.
- Files to inspect: `docker-compose.noauth.yml`, governance security config, workspace policy URL config
- Files to modify: `docker-compose.noauth.yml`, `README.md`
- Exact change to make: Either set governance `FOS_SECURITY_ENABLED=false` in the no-auth overlay, or explicitly route workspace policy checks to `fos-opa-mock` and document that governance APIs remain protected. Prefer consistency for local no-auth: disable governance security only in the no-auth overlay.
- Safety rule: Do not change default full compose security.
- Verification command: `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- Expected result: The overlay clearly resolves `FOS_SECURITY_ENABLED=false` for every service intended to bypass auth.
- What to do if verification fails: Keep existing overlay and document the limitation.

### Step 3: Make OpenSearch optional after human decision
- Objective: Reduce minimal local stack weight if OpenSearch is unused.
- Files to inspect: `docker-compose.yml`, `README.md`
- Files to modify: `docker-compose.yml`, `README.md`
- Exact change to make: If MANUAL-009 approves optionalization, add `profiles: ["opensearch"]` to the `opensearch` service and document `docker compose --profile opensearch up -d`. If user confirms it is required, document which service depends on it.
- Safety rule: Do not delete OpenSearch volumes or data.
- Verification command: `docker compose config`
- Expected result: OpenSearch behavior matches user decision.
- What to do if verification fails: Revert profile change and keep OpenSearch as-is.

### Step 4: Make `start.ps1` safer or document it as heavy
- Objective: Prevent accidental full rebuilds/stops when user wants minimal startup.
- Files to inspect: `start.ps1`, `README.md`
- Files to modify: `start.ps1`, `README.md`
- Exact change to make: Either add prompts and comments warning that it stops/rebuilds the full stack, or add a separate non-destructive helper. Do not remove the script without user approval.
- Safety rule: Do not add `down -v`, volume deletion, or destructive cleanup.
- Verification command: `powershell -NoProfile -Command "Get-Content .\\start.ps1 | Select-String 'down|build|up'"`
- Expected result: Script behavior is clear and not more destructive than before.
- What to do if verification fails: Restore previous `start.ps1`.

## 9. Verification Commands
- `docker compose config`
- `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- `docker compose --env-file .env.dev -f docker-compose.infra.yml config`
- `docker compose ps`

## 10. Acceptance Criteria
- [ ] Full compose has a reachable OPA or explicit local policy mock.
- [ ] Governance `OPA_URL` does not point to a missing host service in full Docker.
- [ ] No-auth overlay behavior is explicit and internally consistent.
- [ ] OpenSearch is either justified or optionalized after user confirmation.
- [ ] No destructive Docker commands are introduced.

## 11. Rollback Plan
Restore previous compose files and `start.ps1` from Git. Do not remove volumes. If containers were started, stop them only with non-volume commands after user approval.

## 12. Notes For The Execution Agent
Do not change policy rules here. Coordinate with `03-keycloak-auth-security-plan.md` because policy behavior depends on real roles and token claims.
