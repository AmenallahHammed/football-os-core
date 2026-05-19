# 07 — Frontend Cleanup

## 1. Purpose
Separate live Angular runtime code from mock/demo/obsolete UI code and reduce build warnings without deleting anything unless the user confirms.

## 2. Source Errors From erreurs.md
- Section 6: Frontend Problems
- Section 10: Frontend cleanup list
- Section 11: Frontend placeholder/demo/fake values
- Section 12: Runtime data mismatch

## 3. Classification
- Remove direct workspace `8082` calls if found: Agent-fixable.
- Replace hardcoded team/actor IDs with config/auth data: Mixed; needs MANUAL-003 for real IDs.
- Delete/archive obsolete frontend folders: Needs human confirmation; see MANUAL-007.
- Reduce Angular budget warnings: Agent-fixable.
- Replace mock data service usage: Agent-fixable only after backend endpoints exist; otherwise mixed.

## 4. Files Allowed To Modify
- `fos-workspace-frontend/src/app/app.routes.ts`
- `fos-workspace-frontend/src/app/features/workspace-calendar/`
- `fos-workspace-frontend/src/app/features/documents/`
- `fos-workspace-frontend/src/app/features/players/`
- `fos-workspace-frontend/src/app/features/player-profile/`
- `fos-workspace-frontend/src/app/features/notifications/`
- `fos-workspace-frontend/src/app/features/inbox/`
- `fos-workspace-frontend/src/app/features/workspace-profile/`
- `fos-workspace-frontend/src/app/core/data/workspace-data.service.ts`
- `fos-workspace-frontend/src/app/core/layout/`
- `fos-workspace-frontend/src/environments/`
- `fos-workspace-frontend/angular.json`
- Frontend tests under `fos-workspace-frontend/src/app/`

## 5. Files Forbidden To Touch
- Global forbidden folders: `report/`, `reports/`, `rapport/`, `rapports/`, `target/`, `node_modules/`, `dist/`, `.git/`
- Do not delete `fos-workspace-frontend/src/app/features/calendar/`, `shared/onlyoffice-editor/`, or `frontend-plan/` until MANUAL-007 is answered.
- Do not edit backend source in this plan.

## 6. Current Problem Summary
The frontend mixes live backend API screens with old mock screens and local fallback data. Some routed pages still use `WorkspaceDataService`. Calendar and profile code hardcode fake UUIDs. Build succeeds but reports budget warnings.

## 7. Target State
The active route tree is clear. Mock/demo data is either removed after confirmation or isolated as fallback/dev-only. Hardcoded fake UUIDs are replaced with auth/config/backend data where possible. Build warnings are either fixed or explicitly accepted.

## 8. Step-by-Step Execution Plan
### Step 1: Confirm active routes and unused folders
- Objective: Identify what is active before cleanup.
- Files to inspect: `app.routes.ts`, `features/calendar/`, `features/workspace-calendar/`, `shared/onlyoffice-editor/`, `features/workspace-onlyoffice/`
- Files to modify: None until MANUAL-007 is answered
- Exact change to make: Produce a short note listing active and inactive components. If user confirmed deletion, remove imports/routes/files for obsolete folders; otherwise leave files untouched and document.
- Safety rule: No deletion without MANUAL-007.
- Verification command: `rg -n "CalendarPageComponent|OnlyofficeEditorComponent|app-calendar-page|app-onlyoffice-editor" fos-workspace-frontend\\src\\app`
- Expected result: References are understood and no accidental route removal occurs.
- What to do if verification fails: Stop and ask for confirmation.

### Step 2: Replace fake team ID strategy
- Objective: Remove hardcoded `00000000-0000-0000-0000-000000000001` from calendar API calls.
- Files to inspect: `workspace-calendar-api.service.ts`, `AuthService`, environment files
- Files to modify: `workspace-calendar-api.service.ts`, possibly environment files or a small config service
- Exact change to make: Use team/club ID from auth claims or a documented environment fallback. If real team UUID is required, pause for MANUAL-003.
- Safety rule: Do not invent production IDs.
- Verification command: `rg -n "00000000-0000-0000-0000-000000000001" fos-workspace-frontend\\src\\app`
- Expected result: No runtime hardcoded fake team ID remains, or remaining instances are tests/docs only.
- What to do if verification fails: Keep fallback but move it to explicit dev config and document manual requirement.

### Step 3: Isolate or replace `WorkspaceDataService`
- Objective: Stop mock data from masquerading as live runtime data.
- Files to inspect: `workspace-data.service.ts` and all references
- Files to modify: Components using mock data only when backend APIs exist
- Exact change to make: For notifications, players, and profile pages, either create backend API services if endpoints exist or label fallback behavior as dev-only. Do not delete service until all consumers are migrated.
- Safety rule: Do not break routed pages for lack of backend endpoint.
- Verification command: `rg -n "WorkspaceDataService" fos-workspace-frontend\\src\\app`
- Expected result: Usage count decreases or remaining uses are documented as fallback.
- What to do if verification fails: Revert component migration and keep mock fallback.

### Step 4: Address Angular budget warnings
- Objective: Make build output acceptable or document budget decision.
- Files to inspect: `angular.json`, landing page SCSS, large feature components
- Files to modify: `angular.json` or specific SCSS/TS files
- Exact change to make: Prefer reducing landing SCSS below budget. For initial bundle budget, either lazy-load large routes or adjust budget only if user accepts current size. Do not perform a broad frontend architecture rewrite.
- Safety rule: Keep UI behavior intact.
- Verification command: `npm run build`
- Expected result: Build succeeds; warnings are reduced or documented.
- What to do if verification fails: Revert budget/code changes and capture output.

## 9. Verification Commands
- `rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend\\src`
- `rg -n "00000000-0000-0000-0000-000000000001|11111111-1111-1111-1111-111111111101" fos-workspace-frontend\\src\\app`
- `rg -n "WorkspaceDataService" fos-workspace-frontend\\src\\app`
- `npm run build`

## 10. Acceptance Criteria
- [ ] No frontend source calls workspace port `8082` directly.
- [ ] Fake UUIDs are removed from runtime paths or explicitly dev-only.
- [ ] Obsolete folders are not deleted without human confirmation.
- [ ] Angular build succeeds.
- [ ] Budget warnings are fixed or documented as accepted.

## 11. Rollback Plan
Restore changed frontend files from Git. If obsolete folders were deleted after approval, restore them from Git if the user changes direction. Do not touch `dist/`.

## 12. Notes For The Execution Agent
This plan is cleanup, not redesign. Keep app usable. Coordinate with plan 03 for auth claims and plan 09 for final build verification.
