# Step 07 — Frontend Runtime Cleanup

## 1. Purpose
Clean active Angular runtime behavior without redesigning the UI. This step removes direct workspace calls, isolates fake UUIDs and mock/demo data, classifies obsolete folders without deletion, and keeps calendar/documents behavior buildable through gateway APIs.

## 2. Errors Covered
- Runtime frontend code may call workspace directly on port `8082`.
- Fake UUIDs may appear in runtime API paths.
- Mock/demo data may masquerade as live backend data.
- Old frontend folders/components need classification but not automatic deletion.
- Angular build warnings may need reduction or documentation.
- Calendar and documents flows must keep API calls through the gateway.

## 3. Files To Inspect
- `fos-workspace-frontend/src/app/app.routes.ts`
- `fos-workspace-frontend/src/app/core/data/workspace-data.service.ts`
- `fos-workspace-frontend/src/app/core/auth/`
- `fos-workspace-frontend/src/app/features/workspace-calendar/`
- `fos-workspace-frontend/src/app/features/calendar/`
- `fos-workspace-frontend/src/app/features/documents/`
- `fos-workspace-frontend/src/app/features/workspace-onlyoffice/`
- `fos-workspace-frontend/src/app/shared/onlyoffice-editor/`
- `fos-workspace-frontend/src/app/features/players/`
- `fos-workspace-frontend/src/app/features/player-profile/`
- `fos-workspace-frontend/src/app/features/notifications/`
- `fos-workspace-frontend/src/app/features/inbox/`
- `fos-workspace-frontend/src/app/features/workspace-profile/`
- `fos-workspace-frontend/src/environments/`
- `fos-workspace-frontend/angular.json`
- `README.md`

## 4. Files Allowed To Modify
- `fos-workspace-frontend/src/app/app.routes.ts`
- `fos-workspace-frontend/src/app/core/data/workspace-data.service.ts`
- `fos-workspace-frontend/src/app/core/auth/`
- `fos-workspace-frontend/src/app/features/workspace-calendar/`
- `fos-workspace-frontend/src/app/features/documents/`
- `fos-workspace-frontend/src/app/features/workspace-onlyoffice/`
- `fos-workspace-frontend/src/app/features/players/`
- `fos-workspace-frontend/src/app/features/player-profile/`
- `fos-workspace-frontend/src/app/features/notifications/`
- `fos-workspace-frontend/src/app/features/inbox/`
- `fos-workspace-frontend/src/app/features/workspace-profile/`
- `fos-workspace-frontend/src/environments/environment.ts`
- `fos-workspace-frontend/src/environments/environment.development.ts`
- `fos-workspace-frontend/angular.json`
- Frontend tests under `fos-workspace-frontend/src/app/`
- `README.md`

## 5. Files Forbidden To Modify
- `report/`
- `reports/`
- `rapport/`
- `rapports/`
- `target/`
- `node_modules/`
- `dist/`
- `.git/`
- Backend source files.
- `fos-workspace-frontend/src/app/features/calendar/` unless user explicitly approves deletion or edits.
- `fos-workspace-frontend/src/app/shared/onlyoffice-editor/` unless user explicitly approves deletion or edits.
- `fos-workspace-frontend/frontend-plan/` unless user explicitly approves deletion or edits.

## 6. Automatic Fixes To Perform
1. Remove direct workspace API calls.
   - Objective: Ensure Angular runtime calls gateway only.
   - Exact implementation instruction: Replace runtime `localhost:8082` or `http://localhost:8082` references with environment gateway base URL. Keep local default gateway URL as `http://localhost:8080`.
   - Safety rule: Do not hardcode backend service ports in feature services.
   - Verification command: `rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend/src`
   - Expected result: No runtime source calls workspace directly.
   - Stop condition if it fails: Stop if remaining hits are documentation/test-only and classify them rather than editing blindly.

2. Move fake UUIDs out of runtime paths.
   - Objective: Prevent fake IDs from silently driving real workflows.
   - Exact implementation instruction: Search for `00000000-0000-0000-0000-000000000001` and `11111111-1111-1111-1111-111111111101`. Remove them from runtime API construction. If no real source exists, move fallback values to `environment.development.ts` with explicit names like `devFallbackTeamId`, `devFallbackClubId`, or `devFallbackActorId`.
   - Safety rule: Do not invent production IDs.
   - Verification command: `rg -n "00000000-0000-0000-0000-000000000001|11111111-1111-1111-1111-111111111101" fos-workspace-frontend/src/app`
   - Expected result: Fake UUIDs are absent from runtime logic or clearly isolated as dev-only fallback config.
   - Stop condition if it fails: Record `MANUAL-003` or `MANUAL-010` if real IDs are required.

3. Isolate mock/demo data.
   - Objective: Stop mock data from pretending to be live backend data.
   - Exact implementation instruction: Find all `WorkspaceDataService` consumers. Where a real gateway API exists, use it. Where no backend endpoint exists, keep mock behavior but name and document it as development fallback.
   - Safety rule: Do not break routed pages just because an endpoint is missing.
   - Verification command: `rg -n "WorkspaceDataService" fos-workspace-frontend/src/app`
   - Expected result: Mock usage is reduced or explicitly documented as dev-only.
   - Stop condition if it fails: Stop if replacing mock data requires new backend APIs outside this step.

4. Classify obsolete folders without deletion.
   - Objective: Make active and inactive frontend areas clear.
   - Exact implementation instruction: Inspect route references and imports for old calendar and OnlyOffice components. Document active vs inactive folders in README or handoff notes.
   - Safety rule: Do not delete or archive old folders without explicit user confirmation.
   - Verification command: `rg -n "CalendarPageComponent|OnlyofficeEditorComponent|app-calendar-page|app-onlyoffice-editor" fos-workspace-frontend/src/app`
   - Expected result: Active references are understood and no folder is deleted automatically.
   - Stop condition if it fails: Record `MANUAL-007`.

5. Address Angular build warnings conservatively.
   - Objective: Keep build output acceptable without redesign.
   - Exact implementation instruction: Run build. If warnings are from oversized component styles, reduce local styles only when behavior stays unchanged. If warnings are from bundle budgets, document them unless a small lazy-load or budget adjustment is clearly safe.
   - Safety rule: Do not redesign UI or perform broad frontend architecture changes.
   - Verification command: `cd fos-workspace-frontend; npm run build`
   - Expected result: Build succeeds; warnings are fixed or documented.
   - Stop condition if it fails: Revert frontend cleanup changes and record build output.

## 7. Manual-Only Blockers
- `MANUAL-003`: User must provide real IDs when runtime behavior requires business data.
- `MANUAL-007`: User must approve deleting or archiving obsolete frontend folders.
- `MANUAL-010`: User must provide real team/document UUIDs for final smoke tests.

## 8. Verification Commands
- `rg -n "localhost:8082|http://localhost:8082" fos-workspace-frontend/src`
- `rg -n "00000000-0000-0000-0000-000000000001|11111111-1111-1111-1111-111111111101" fos-workspace-frontend/src/app`
- `rg -n "WorkspaceDataService" fos-workspace-frontend/src/app`
- `rg -n "CalendarPageComponent|OnlyofficeEditorComponent|app-calendar-page|app-onlyoffice-editor" fos-workspace-frontend/src/app`
- `cd fos-workspace-frontend; npm run build`

## 9. Acceptance Criteria
- [ ] No runtime Angular source calls workspace `8082` directly.
- [ ] Fake UUIDs are removed from runtime paths or explicitly dev-only.
- [ ] Mock/demo data is isolated or clearly labeled as fallback.
- [ ] Obsolete folders are classified but not deleted without user approval.
- [ ] Angular build succeeds.
- [ ] Calendar and documents flows continue to use gateway APIs.

## 10. Documentation To Update
Update `README.md` or final handoff notes with frontend runtime API rules, dev-only fallback data, obsolete folder classification, and accepted build warnings if any remain.

## 11. Rollback Plan
Revert only frontend files, tests, Angular config, and documentation changed in this step. If any old folder was deleted after explicit user approval, restore it from Git if rollback is requested. Do not touch `dist/`.
