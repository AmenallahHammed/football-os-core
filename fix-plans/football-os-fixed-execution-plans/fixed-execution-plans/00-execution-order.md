# Football OS Error Repair — Execution Order

## Purpose
This file is the only entry point for the coding agent.

The previous repair plans were too fragmented and conditional. This replacement plan gives the agent one clear execution order, strict boundaries, and explicit stop conditions.

## Golden Rule
Fix the errors found in `erreurs.md`. Do not redesign the project, do not delete uncertain files, and do not invent production data.

## Required Reading Order
1. `erreurs.md`
2. `fix-plans/00-execution-order.md`
3. `fix-plans/01-agent-fix-plan.md`
4. `fix-plans/02-manual-fixes.md`
5. `fix-plans/03-verification-checklist.md`

## Files To Place In Repo
Create this folder at the repository root:

```text
fix-plans/
  00-execution-order.md
  01-agent-fix-plan.md
  02-manual-fixes.md
  03-verification-checklist.md
```

## Strict Execution Order
Execute the categories in this exact order:

1. Environment templates and runtime profiles
2. Docker Compose, OPA, and no-auth consistency
3. Keycloak realm import and role claim handling
4. Gateway, workspace, and governance service flow
5. MinIO storage and OnlyOffice integration
6. Frontend cleanup
7. Backend cleanup
8. Final verification

Do not skip earlier categories because later categories depend on them.

## Global Forbidden Paths
Never edit, delete, move, or criticize these folders unless the user explicitly asks:

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

Also do not delete these without explicit written user approval:

```text
AI_CONTEXT/
diagrams/
fos-workspace-frontend/frontend-plan/
fos-workspace-frontend/src/app/features/calendar/
fos-workspace-frontend/src/app/shared/onlyoffice-editor/
```

## Global Architecture Rules
These rules must remain true after every fix:

- Frontend API calls must go through the gateway at `http://localhost:8080`.
- Frontend must not call workspace directly at `http://localhost:8082`.
- Workspace must call governance through SDK clients, not governance internals.
- Authorization checks must go through `PolicyClient`.
- Storage operations must go through `StoragePort`.
- Kafka consumers must extend `AbstractFosConsumer`.
- Mongo documents must extend `BaseDocument`.
- Mongo schema/index changes must use Mongock.
- Do not introduce raw `MongoClient` in domain/application layers.
- Do not physically delete domain data when archive/soft-delete exists.
- Maven versions must remain `0.1.0-SNAPSHOT`.
- Do not commit changes unless the user explicitly asks.

## Manual Blocker Rule
If a task needs local secrets, Docker Desktop access, browser session state, real UUIDs, real database objects, or product judgment, do not guess.

Instead:

1. Stop the current category.
2. Record the blocker name from `02-manual-fixes.md`.
3. Continue only with unrelated safe categories if possible.

## Stop Conditions
Stop and report immediately if any of these happen:

- A verification command fails and the failure is not obviously caused by missing local software or Docker not running.
- A required file does not exist and no equivalent file is obvious.
- A change would require deleting data, volumes, database rows, buckets, or generated files.
- A change would require real user secrets or production credentials.
- The agent is about to choose between two architecture options not explicitly allowed by this plan.

## Completion Definition
The work is complete only when:

- Every agent-fixable issue from `erreurs.md` is fixed or clearly marked blocked by a manual item.
- Every manual-only issue is listed in `02-manual-fixes.md`.
- All verification commands in `03-verification-checklist.md` are run or blocked for a documented reason.
- No forbidden folders were edited.
- The final report includes changed files, commands run, command results, remaining blockers, and next manual actions.
