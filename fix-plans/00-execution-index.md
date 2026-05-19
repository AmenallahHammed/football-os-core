# Football OS Fix Plans — Execution Index

## Purpose
This folder converts `erreurs.md` into executable repair plans. Each plan is scoped to one logical category so a future coding agent can execute it without improvising or touching unrelated files.

## Required Reading Order
1. `erreurs.md`
2. This index
3. `manual-fixes.md`
4. Plan files in numeric order

## Recommended Execution Order
1. `01-env-and-profiles-plan.md`
2. `02-docker-and-infrastructure-plan.md`
3. `03-keycloak-auth-security-plan.md`
4. `04-gateway-workspace-governance-plan.md`
5. `05-minio-storage-plan.md`
6. `06-onlyoffice-plan.md`
7. `07-frontend-cleanup-plan.md`
8. `08-backend-cleanup-plan.md`
9. `09-verification-plan.md`

## Plan Map
| Plan File | Category | Errors Covered | Manual Dependencies | Can Execute Automatically? |
|---|---|---|---|---|
| `01-env-and-profiles-plan.md` | Environment files, Spring profiles, CORS, frontend envs | ERR-003 env part, ERR-006 env part, Sections 4, 5, 11, 12 env/secret/profile/CORS mismatches | MANUAL-004, MANUAL-005 | Mostly yes |
| `02-docker-and-infrastructure-plan.md` | Compose, OPA, OpenSearch, Docker helper scripts | ERR-002, Section 8, Section 10 compose/helper entries, Section 12 OPA/no-auth mismatch | MANUAL-001, MANUAL-009 | Mostly yes |
| `03-keycloak-auth-security-plan.md` | Keycloak realm import, token claims, role extraction | ERR-001, ERR-004, Sections 6, 7, 12 Keycloak mismatches | MANUAL-002, MANUAL-003 | Mixed |
| `04-gateway-workspace-governance-plan.md` | Gateway/workspace/governance call flow and no-auth mode | Section 7 policy/client problems, Section 8 no-auth mismatch, Section 12 runtime/service mismatches | MANUAL-003 | Yes |
| `05-minio-storage-plan.md` | MinIO endpoints, storage provider, document object flow | Section 4 storage vars, Section 9 MinIO reachability, Section 12 storage/provider mismatch | MANUAL-008, MANUAL-010 | Mixed |
| `06-onlyoffice-plan.md` | OnlyOffice callback, JWT, URLs, file-type compatibility | ERR-003 OnlyOffice part, ERR-005, ERR-006 OnlyOffice part, Sections 9, 11, 12 OnlyOffice mismatches | MANUAL-005, MANUAL-008, MANUAL-010 | Mostly yes |
| `07-frontend-cleanup-plan.md` | Angular mock data, obsolete components, build budgets | Section 6, Section 10 frontend cleanup entries, Section 11 demo/fake frontend values | MANUAL-007 | Mixed |
| `08-backend-cleanup-plan.md` | Backend fallbacks, run scripts, service hygiene | Section 7 backend problems, Section 10 backend/docs cleanup entries, Section 11 backend fake values | MANUAL-003, MANUAL-010 | Mostly yes |
| `09-verification-plan.md` | Final checks across all categories | Section 13 verification results and all acceptance criteria | MANUAL-001, MANUAL-008 | Mixed |

## Global Rules
- Do not edit, inspect, criticize, or delete `report/`, `reports/`, `rapport/`, or `rapports/`.
- Do not edit generated folders: `target/`, `node_modules/`, `dist/`, `.git/`.
- Do not skip verification commands. If a command cannot run, record the exact failure and stop if it blocks the plan.
- Do not commit unless the user explicitly asks.
- If a task needs user judgment, put it in or reference `manual-fixes.md`.
- Preserve architecture rules:
  - All storage work goes through `StoragePort`.
  - All permission checks go through `PolicyClient`.
  - Kafka consumers must use `AbstractFosConsumer`.
  - Mongo documents must extend `BaseDocument`.
  - Mongo schema/index changes must use Mongock.
  - No raw `MongoClient` in domain/application layers.
  - Workspace must depend on SDK modules, not governance internals.
  - Soft delete only; use `ARCHIVED`, never physical removal.
  - Ownership uses `CanonicalRef`.
  - Maven version must remain `0.1.0-SNAPSHOT`.

## Completion Definition
All plans are complete when every issue in `erreurs.md` is either fixed in code/config, explicitly verified as resolved, or recorded as a remaining manual action in `manual-fixes.md`; all acceptance criteria are checked; and final verification in `09-verification-plan.md` passes without new critical errors.
