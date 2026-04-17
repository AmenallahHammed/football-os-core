# Phase 0 Completion Checklist (Ordered)

## 0) Environment Gate (must pass first)
- [ ] Docker Desktop healthy and reachable by Testcontainers (no `Npipe ... Status 400` errors)
- [ ] `mvn -q -DskipTests install` works at repo root
- [ ] `docker-compose --env-file .env.example config` succeeds
- [ ] `docker-compose --env-file .env.example up -d` shows healthy core services

---

## 1) Sprint 0.3 Functional Gaps (Identity + Canonical CRUD)
- [x] Add Actor update endpoint (`PUT`/`PATCH`) and service logic
- [x] Add Player update endpoint (`PUT`/`PATCH`) and service logic
- [x] Add Team update endpoint (`PUT`/`PATCH`) and service logic
- [x] Emit update FACT events for Actor/Player/Team updates
- [x] Add/adjust tests proving create/read/update/deactivate flows

Acceptance:
- [x] CRUD is true CRUD for Actor/Player/Team (not create/read/deactivate-only)

---

## 2) Sprint 0.4 Policy Semantics
- [x] Change policy API behavior so DENY maps to HTTP `403` (not `200`)
- [x] Keep ALLOW as `200`
- [x] Update policy integration tests accordingly

Acceptance:
- [x] ALLOW -> `200`
- [x] DENY -> `403`

---

## 3) Sprint 0.5 Gateway Alignment
- [x] Confirm gateway JWT path works with local Keycloak defaults
- [x] Reconcile rate-limit config to actual "100 req/min per actor" behavior
- [x] Validate correlation + actor header enrichment on proxied routes
- [ ] Ensure all gateway tests pass (JWT, correlation, rate limit)

Acceptance:
- [x] Valid JWT -> `200`
- [x] Missing/invalid JWT -> `401`
- [ ] Exceeded rate limit -> `429`
- [x] Header propagation verified (`X-FOS-Request-Id`, `X-FOS-Actor-Id`)

---

## 4) Sprint 0.6 sdk-test Completeness
- [x] Add missing sdk-test unit tests under `fos-sdk/sdk-test/src/test/java`
  - [x] `MockActorFactoryTest`
  - [x] `SignalCaptorTest`
- [x] Add missing governance test if required by sprint plan:
  - [x] `SdkClientWiringTest` (or equivalent documented replacement)
- [x] Ensure governance integration tests consistently extend `FosTestContainersBase`

Acceptance:
- [x] sdk-test has both `src/main` and `src/test` coverage as planned
- [x] all declared test utilities are tested

---

## 5) End-to-End Smoke Coverage vs Sprint Goal
- [x] Expand `Phase0SmokeTest` to include full intended chain:
  - [x] actor created
  - [x] JWT issued/used through gateway path
  - [x] policy evaluation checked
  - [x] storage operation via `sdk-storage`
  - [x] audit written and verified
- [x] Remove ad-hoc sleeps where possible (prefer deterministic waits/utilities)

Acceptance:
- [x] smoke test validates complete Phase 0 narrative, not partial flow

---

## 6) Security + Config Hygiene Finalization
- [x] Confirm webhook secret required and documented
- [x] Confirm compose secrets are env-driven only
- [x] Keep `.env.example` placeholders complete and non-sensitive
- [x] Verify no generated artifacts are tracked (`bin/**`, IDE metadata)

Acceptance:
- [x] no hardcoded admin secrets
- [x] clean workspace policy enforced

---

## 7) Final Verification Matrix (release gate)
Run and record pass/fail:
- [ ] `mvn -pl fos-governance-service test`
- [ ] `mvn -pl fos-gateway test`
- [x] `mvn -pl fos-sdk test`
- [ ] `mvn test` at repo root
- [x] `docker-compose --env-file .env.example ps` all healthy
- [ ] quick manual API smoke through gateway

Current run notes:
- `fos-governance-service` test suite fails due Testcontainers Docker detection (`Npipe ... Status 400`).
- `fos-gateway` test suite mostly passes; `GatewayRateLimitTest` fails for the same Testcontainers Docker detection issue.
- root `mvn test` fails in governance stage for the same environment issue.
- quick manual gateway smoke was started but command was interrupted before completion.

Release criteria:
- [ ] 0 failing tests in CI target set
- [ ] all Phase 0 sprint acceptance points satisfied
- [ ] architecture rule respected (domains use SDK boundary, no governance internals)

---

## 8) Documentation/Traceability Closeout
- [ ] Update implementation summary to reflect real status (remove "fully complete" claims if still pending)
- [ ] Add known limitations + deferred items explicitly
- [ ] Keep `issues.md` and this checklist in sync with DONE/TODO/BLOCKED states
