# Football OS 10-Step Fix Process

## 1. Purpose
This folder consolidates the existing Football OS fix plans into one strict execution process for a future coding agent. The process exists to fix, verify, and document the known local development, auth, storage, gateway, frontend, backend, and integration errors without improvising or touching unrelated files.

The required root input folder `football-os-execution-plans/` is absent in this repository. Use `fix-plans/football-os-fixed-execution-plans/fixed-execution-plans/` as the intended second input source.

## 2. Required Reading Order
1. `fix-plans/00-execution-index.md`
2. `fix-plans/manual-fixes.md`
3. `fix-plans/01-env-and-profiles-plan.md`
4. `fix-plans/02-docker-and-infrastructure-plan.md`
5. `fix-plans/03-keycloak-auth-security-plan.md`
6. `fix-plans/04-gateway-workspace-governance-plan.md`
7. `fix-plans/05-minio-storage-plan.md`
8. `fix-plans/06-onlyoffice-plan.md`
9. `fix-plans/07-frontend-cleanup-plan.md`
10. `fix-plans/08-backend-cleanup-plan.md`
11. `fix-plans/09-verification-plan.md`
12. `fix-plans/football-os-fixed-execution-plans/fixed-execution-plans/00-execution-order.md`
13. `fix-plans/football-os-fixed-execution-plans/fixed-execution-plans/01-agent-fix-plan.md`
14. `fix-plans/football-os-fixed-execution-plans/fixed-execution-plans/02-manual-fixes.md`
15. `fix-plans/football-os-fixed-execution-plans/fixed-execution-plans/03-verification-checklist.md`
16. This file
17. Step files in numeric order

## 3. Execution Order
1. `01-step-01-environment-and-profiles.md`
2. `02-step-02-docker-infrastructure-and-opa.md`
3. `03-step-03-keycloak-auth-and-role-claims.md`
4. `04-step-04-gateway-workspace-governance-flow.md`
5. `05-step-05-minio-storage-flow.md`
6. `06-step-06-onlyoffice-integration.md`
7. `07-step-07-frontend-runtime-cleanup.md`
8. `08-step-08-backend-cleanup-and-safety.md`
9. `09-step-09-final-verification.md`
10. `10-step-10-fix-documentation-and-handoff.md`

## 4. Step Map
| Step file | Category | Main errors covered | Manual blockers | Expected verification |
|---|---|---|---|---|
| `01-step-01-environment-and-profiles.md` | Environment and profiles | Weak placeholders, `.env.dev.example` absence, env naming mismatch, hardcoded CORS, frontend env defaults | Local `.env` secrets, LAN IP | Env template checks, ignored env checks, compose config, gateway compile, frontend build |
| `02-step-02-docker-infrastructure-and-opa.md` | Docker infrastructure and OPA | Full compose OPA mismatch, no-auth inconsistency, OpenSearch local decision, Docker helper safety | Docker Desktop, OpenSearch decision | Compose config, no-auth merged config, non-destructive script inspection |
| `03-step-03-keycloak-auth-and-role-claims.md` | Keycloak auth and role claims | Missing realm import, frontend client mismatch, incomplete role claim extraction, `fos_club_id` strategy | Keycloak Admin UI, real UUIDs, browser refresh | Compose config, realm JSON search, SDK security tests/compile, discovery endpoint |
| `04-step-04-gateway-workspace-governance-flow.md` | Gateway/workspace/governance | Frontend direct workspace calls, missing gateway route coverage, no-auth flow mismatch, policy client boundaries, upload actor semantics | Real actor/club IDs | Gateway/workspace compile, static route/API searches, no-auth config |
| `05-step-05-minio-storage-flow.md` | MinIO storage | Storage mode confusion, endpoint split, bucket naming, public reachability, `StoragePort` architecture | Real MinIO objects, LAN IP, real document IDs | Env searches, storage architecture search, SDK/workspace compile |
| `06-step-06-onlyoffice-integration.md` | OnlyOffice | Short JWT placeholder, callback URL mismatch, unsigned callback acceptance, file type mismatch, public endpoint boundary | Local secret, real document data, LAN endpoint | Compose OnlyOffice config, workspace tests/compile, frontend build, `api.js` check |
| `07-step-07-frontend-runtime-cleanup.md` | Frontend runtime cleanup | Direct `8082` calls, fake UUIDs, mock/demo data, obsolete folders, build warnings, calendar/doc behavior | Deletion approval, real IDs | Static searches and Angular build |
| `08-step-08-backend-cleanup-and-safety.md` | Backend cleanup and safety | Missing `-am`, fallback UUID boundaries, architecture constraints, Mongo/Mongock, Kafka, Maven version | Real UUIDs | Script search, backend compile, architecture searches, Maven version search |
| `09-step-09-final-verification.md` | Final verification | Whole-system compile/build/config/runtime smoke checks | Docker Desktop, Keycloak UI, browser session, real data | Git status, backend compile, frontend build, compose validation, health/discovery/API checks |
| `10-step-10-fix-documentation-and-handoff.md` | Documentation and handoff | Final report, manual work, command results, changed files, risks | Missing verification results from earlier steps | Report file or final handoff text reviewed against template |

## 5. Global Forbidden Folders
Never edit, delete, move, or generate files inside:

```text
report/
reports/
rapport/
rapports/
target/
node_modules/
dist/
.git/
```

Also do not delete these without explicit user confirmation:

```text
AI_CONTEXT/
diagrams/
fos-workspace-frontend/frontend-plan/
fos-workspace-frontend/src/app/features/calendar/
fos-workspace-frontend/src/app/shared/onlyoffice-editor/
```

## 6. Global Architecture Rules
- Frontend API calls must go through the gateway at port `8080`, not workspace directly at port `8082`.
- Storage operations must go through `StoragePort`.
- Permission and policy checks must go through `PolicyClient`.
- Kafka consumers must go through `AbstractFosConsumer`.
- Workspace must not depend on governance internals.
- Mongo documents must extend `BaseDocument`.
- Mongo schema and index changes must use Mongock.
- Domain deletion must be soft delete only; use `ARCHIVED`.
- Ownership references must use `CanonicalRef`.
- Maven versions must remain `0.1.0-SNAPSHOT`.
- Do not commit changes unless the user explicitly asks.
- Do not invent production secrets, users, UUIDs, buckets, documents, or LAN addresses.
- Do not run destructive Docker commands, volume deletion, bucket deletion, or database cleanup.

## 7. Definition Of Done
- All 10 step files have been executed in order, or stopped on explicit manual blockers.
- Every agent-fixable issue from the source plans is fixed or assigned to the owning step with a stop condition.
- Every manual-only issue is recorded with the exact manual blocker name and required user input.
- Final verification either passes or identifies the owning failed step and stops.
- No forbidden folders, generated folders, real env files, secrets, Docker volumes, MinIO objects, or database data were modified.
- Final handoff documents changed files, commands run, results, remaining manual blockers, and residual risks.
