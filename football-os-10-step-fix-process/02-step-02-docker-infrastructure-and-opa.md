# Step 02 — Docker Infrastructure And OPA

## 1. Purpose
Fix Docker Compose infrastructure so full local Docker has a reachable policy service, no-auth mode is internally consistent, Docker helper behavior is safe, and verification can run without destructive commands.

## 2. Errors Covered
- Full `docker-compose.yml` references OPA/policy behavior without starting a reachable OPA service.
- `docker-compose.noauth.yml` may disable security for gateway/workspace while governance remains secured.
- OpenSearch default local behavior is unclear.
- Docker helper scripts may stop or rebuild more than users expect.
- Docker verification must not use destructive volume commands.

## 3. Files To Inspect
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `docker-compose.noauth.yml`
- `.env.example`
- `.env.dev.example`
- `README.md`
- `start.ps1`
- `opa-mock/default.conf`
- `opa-mock/allow.json`
- `fos-governance-service/src/main/resources/application.yml`
- `fos-governance-service/src/main/resources/opa/`

## 4. Files Allowed To Modify
- `docker-compose.yml`
- `docker-compose.infra.yml`
- `docker-compose.noauth.yml`
- `.env.example`
- `.env.dev.example`
- `README.md`
- `start.ps1`
- `opa-mock/default.conf`
- `opa-mock/allow.json`

## 5. Files Forbidden To Modify
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `.git/`
- Docker volumes
- Database data
- MinIO buckets or objects
- Java policy logic unless a later step explicitly owns it.

## 6. Automatic Fixes To Perform
1. Add a reachable policy service to full compose.
   - Objective: Ensure governance can reach OPA during full Docker startup.
   - Exact implementation instruction: Add a real OPA service to `docker-compose.yml` that mounts `fos-governance-service/src/main/resources/opa/` read-only and exposes an internal service hostname such as `opa:8181`. Configure governance `OPA_URL` to use `http://opa:8181` in full Docker mode. Use `opa-mock` only for no-auth or explicitly local mock modes.
   - Safety rule: Do not alter Rego policy semantics and do not silently replace real policy with allow-all.
   - Verification command: `docker compose config`
   - Expected result: Full compose includes a policy service and governance resolves `OPA_URL` to a service hostname.
   - Stop condition if it fails: Stop if OPA cannot load existing Rego files; report the OPA/Rego error instead of switching to allow-all.

2. Align no-auth overlay across backend services.
   - Objective: Prevent no-auth mode from calling secured governance without JWT.
   - Exact implementation instruction: In `docker-compose.noauth.yml`, set `FOS_SECURITY_ENABLED=false` for gateway, workspace, and governance if those services participate in no-auth local mode. Keep default `docker-compose.yml` secured.
   - Safety rule: No-auth changes must exist only in the overlay.
   - Verification command: `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
   - Expected result: Merged config shows `FOS_SECURITY_ENABLED=false` for gateway, workspace, and governance only in no-auth mode.
   - Stop condition if it fails: Stop if a service uses a different security toggle that cannot be confirmed from its config.

3. Keep OpenSearch unchanged unless explicitly decided.
   - Objective: Avoid making product/dev-experience decisions automatically.
   - Exact implementation instruction: Do not remove OpenSearch, move it behind a profile, or change its volumes unless the user explicitly chooses that option. Document the current behavior and the manual decision item.
   - Safety rule: Do not delete OpenSearch volumes or data.
   - Verification command: `docker compose config`
   - Expected result: OpenSearch behavior remains unchanged unless the user approved a specific change.
   - Stop condition if it fails: Stop and ask for a user decision under `MANUAL-009`.

4. Make Docker helper behavior safe and explicit.
   - Objective: Prevent accidental destructive cleanup or unexpected full rebuilds.
   - Exact implementation instruction: Inspect `start.ps1`. Add comments, prompts, or README warnings if it stops/rebuilds the full stack. Do not add `down -v`, volume deletion, database deletion, or bucket deletion.
   - Safety rule: No destructive Docker commands.
   - Verification command: `Select-String -Path start.ps1 -Pattern "down -v|Remove-Item|docker compose down|docker compose up|build"`
   - Expected result: Script behavior is visible and no destructive volume command is introduced.
   - Stop condition if it fails: Revert `start.ps1` changes and document the current behavior only.

## 7. Manual-Only Blockers
- `MANUAL-001`: User must start or unlock Docker Desktop before runtime Docker verification.
- `MANUAL-009`: User must decide whether OpenSearch belongs in the minimal local stack before changing its default behavior.

## 8. Verification Commands
- `docker compose config`
- `docker compose --env-file .env.dev -f docker-compose.infra.yml config`
- `docker compose -f docker-compose.yml -f docker-compose.noauth.yml config`
- `Select-String -Path start.ps1 -Pattern "down -v|docker compose down|docker compose up|build"`
- `docker compose ps`

## 9. Acceptance Criteria
- [ ] Full compose has a reachable real OPA service or stops with a documented Rego load blocker.
- [ ] Governance `OPA_URL` does not point to a missing endpoint in full Docker mode.
- [ ] No-auth overlay security settings are consistent across gateway, workspace, and governance.
- [ ] OpenSearch is unchanged unless explicitly approved by the user.
- [ ] No destructive Docker commands are added or required.

## 10. Documentation To Update
Update `README.md` with full Docker policy service behavior, no-auth mode scope, OpenSearch manual decision status, safe Docker startup commands, and a warning never to use `docker compose down -v` for this process.

## 11. Rollback Plan
Revert only compose, README, `start.ps1`, and OPA mock files changed in this step. Stop containers only with non-volume commands if the user approves. Never delete Docker volumes, databases, MinIO objects, or Keycloak data as part of rollback.
