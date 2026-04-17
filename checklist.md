# Phase 0 Completion Checklist (Ordered)

## 0) Environment Gate (must pass first)
- [ ] Docker Desktop healthy and reachable by Testcontainers (no `Npipe ... Status 400` errors)
- [ ] `mvn -q -DskipTests install` works at repo root
- [ ] `docker-compose --env-file .env.example config` succeeds
- [ ] `docker-compose --env-file .env.example up -d` shows healthy core services

---

## 1) Sprint 0.3 Functional Gaps (Identity + Canonical CRUD)
- [ ] Add Actor update endpoint (`PUT`/`PATCH`) and service logic
- [ ] Add Player update endpoint (`PUT`/`PATCH`) and service logic
- [ ] Add Team update endpoint (`PUT`/`PATCH`) and service logic
- [ ] Emit update FACT events for Actor/Player/Team updates
- [ ] Add/adjust tests proving create/read/update/deactivate flows

Acceptance:
- [ ] CRUD is true CRUD for Actor/Player/Team (not create/read/deactivate-only)

---

## 2) Sprint 0.4 Policy Semantics
- [ ] Change policy API behavior so DENY maps to HTTP `403` (not `200`)
- [ ] Keep ALLOW as `200`
- [ ] Update policy integration tests accordingly

Acceptance:
- [ ] ALLOW -> `200`
- [ ] DENY -> `403`

---

## 3) Sprint 0.5 Gateway Alignment
- [ ] Confirm gateway JWT path works with local Keycloak defaults
- [ ] Reconcile rate-limit config to actual "100 req/min per actor" behavior
- [ ] Validate correlation + actor header enrichment on proxied routes
- [ ] Ensure all gateway tests pass (JWT, correlation, rate limit)

Acceptance:
- [ ] Valid JWT -> `200`
- [ ] Missing/invalid JWT -> `401`
- [ ] Exceeded rate limit -> `429`
- [ ] Header propagation verified (`X-FOS-Request-Id`, `X-FOS-Actor-Id`)

---

## 4) Sprint 0.6 sdk-test Completeness
- [ ] Add missing sdk-test unit tests under `fos-sdk/sdk-test/src/test/java`
  - [ ] `MockActorFactoryTest`
  - [ ] `SignalCaptorTest`
- [ ] Add missing governance test if required by sprint plan:
  - [ ] `SdkClientWiringTest` (or equivalent documented replacement)
- [ ] Ensure governance integration tests consistently extend `FosTestContainersBase`

Acceptance:
- [ ] sdk-test has both `src/main` and `src/test` coverage as planned
- [ ] all declared test utilities are tested

---

## 5) End-to-End Smoke Coverage vs Sprint Goal
- [ ] Expand `Phase0SmokeTest` to include full intended chain:
  - [ ] actor created
  - [ ] JWT issued/used through gateway path
  - [ ] policy evaluation checked
  - [ ] storage operation via `sdk-storage`
  - [ ] audit written and verified
- [ ] Remove ad-hoc sleeps where possible (prefer deterministic waits/utilities)

Acceptance:
- [ ] smoke test validates complete Phase 0 narrative, not partial flow

---

## 6) Security + Config Hygiene Finalization
- [ ] Confirm webhook secret required and documented
- [ ] Confirm compose secrets are env-driven only
- [ ] Keep `.env.example` placeholders complete and non-sensitive
- [ ] Verify no generated artifacts are tracked (`bin/**`, IDE metadata)

Acceptance:
- [ ] no hardcoded admin secrets
- [ ] clean workspace policy enforced

---

## 7) Final Verification Matrix (release gate)
Run and record pass/fail:
- [ ] `mvn -pl fos-governance-service test`
- [ ] `mvn -pl fos-gateway test`
- [ ] `mvn -pl fos-sdk test`
- [ ] `mvn test` at repo root
- [ ] `docker-compose --env-file .env.example ps` all healthy
- [ ] quick manual API smoke through gateway

Release criteria:
- [ ] 0 failing tests in CI target set
- [ ] all Phase 0 sprint acceptance points satisfied
- [ ] architecture rule respected (domains use SDK boundary, no governance internals)

---

## 8) Documentation/Traceability Closeout
- [ ] Update implementation summary to reflect real status (remove "fully complete" claims if still pending)
- [ ] Add known limitations + deferred items explicitly
- [ ] Keep `issues.md` and this checklist in sync with DONE/TODO/BLOCKED states
