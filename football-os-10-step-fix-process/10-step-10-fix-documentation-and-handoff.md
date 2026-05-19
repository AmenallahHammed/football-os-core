# Step 10 — Fix Documentation And Handoff

## 1. Purpose
Create the final documentation and handoff after fixes and verification. This step records what changed, what was verified, what remains manual, and what risks remain. It is documentation only and must not modify application code.

## 2. Errors Covered
- Final execution results may be missing or scattered.
- Manual blockers may be hidden inside technical notes.
- Commands run and results may not be recorded.
- Changed files and remaining risks may be unclear for the user.
- A future agent or maintainer needs a concise handoff.

## 3. Files To Inspect
- `git status --short`
- Outputs from Steps 01 through 09
- `README.md`
- Existing fix notes under `fix-plans/`
- Any execution report created during the fix process

## 4. Files Allowed To Modify
- `fix-plans/execution-results.md`
- `README.md` only if earlier steps explicitly left documentation updates for final consolidation.
- A handoff section in an existing project documentation file only if the user approves that location.

## 5. Files Forbidden To Modify
- Application source code.
- `.env`
- `.env.dev`
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
- MinIO buckets or objects.

## 6. Automatic Fixes To Perform
1. Create the final execution report.
   - Objective: Record the result of the 10-step process.
   - Exact implementation instruction: Create or update `fix-plans/execution-results.md` using this exact top-level structure: Summary, Changed Files, Commands Run, Issues Fixed, Issues Blocked By Manual Action, Remaining Risks, Handoff Notes, Next Recommended Step.
   - Safety rule: Documentation only; do not edit code while preparing the report.
   - Verification command: `Test-Path fix-plans/execution-results.md; Select-String -Path fix-plans/execution-results.md -Pattern "Summary|Changed Files|Commands Run|Issues Blocked By Manual Action|Remaining Risks|Handoff Notes"`
   - Expected result: Report exists and contains all required sections.
   - Stop condition if it fails: Stop if `fix-plans/` is not an acceptable report location and ask the user for a documentation destination.

2. Document changed files.
   - Objective: Make the scope of work auditable.
   - Exact implementation instruction: Use `git status --short` and the step notes to list changed files grouped by category. Mark pre-existing user changes separately from files changed by the execution agent when known.
   - Safety rule: Do not revert or stage files.
   - Verification command: `git status --short`
   - Expected result: Report lists changed files and identifies any unexpected paths.
   - Stop condition if it fails: Stop if forbidden folders appear in status.

3. Document commands and results.
   - Objective: Preserve verification evidence.
   - Exact implementation instruction: Add a Markdown table with command, result (`pass`, `fail`, `blocked`, or `not run`), and notes. Include all verification commands from Step 09 and focused commands from earlier steps that were actually run.
   - Safety rule: Do not fabricate successful results; use `blocked` or `not run` when appropriate.
   - Verification command: `Select-String -Path fix-plans/execution-results.md -Pattern "| Command | Result | Notes |"`
   - Expected result: Command results table exists.
   - Stop condition if it fails: Stop and repair the documentation table before final handoff.

4. Document manual blockers separately.
   - Objective: Keep user-only actions visible.
   - Exact implementation instruction: List remaining manual blockers by ID, including `MANUAL-001` through `MANUAL-010` only when relevant. For each, include the required user input or action and the owning step.
   - Safety rule: Do not hide manual tasks inside automatic fix descriptions.
   - Verification command: `Select-String -Path fix-plans/execution-results.md -Pattern "MANUAL-"`
   - Expected result: Remaining blockers are explicit or the report says `None.`
   - Stop condition if it fails: Stop and add missing blocker details.

5. Provide handoff notes.
   - Objective: Give the user and future agent the next exact action.
   - Exact implementation instruction: Add concise notes for what was fixed, what remains manual, how to rerun verification, and which step owns any failed check.
   - Safety rule: Do not ask the future agent to improvise.
   - Verification command: `Select-String -Path fix-plans/execution-results.md -Pattern "Handoff Notes|Next Recommended Step"`
   - Expected result: Handoff includes clear next action and no vague choices.
   - Stop condition if it fails: Stop and rewrite vague notes into explicit actions.

## 7. Manual-Only Blockers
None.

## 8. Verification Commands
- `Test-Path fix-plans/execution-results.md`
- `Select-String -Path fix-plans/execution-results.md -Pattern "Summary|Changed Files|Commands Run|Issues Fixed|Issues Blocked By Manual Action|Remaining Risks|Handoff Notes|Next Recommended Step"`
- `git status --short`

## 9. Acceptance Criteria
- [ ] Final execution report exists or final chat handoff contains the same sections.
- [ ] Changed files are documented.
- [ ] Commands run and results are documented.
- [ ] Manual blockers are listed separately or explicitly marked `None.`
- [ ] Remaining risks are documented.
- [ ] Handoff notes tell the user exactly what to do next.
- [ ] No application code is modified in this step.

## 10. Documentation To Update
Create or update `fix-plans/execution-results.md`. Update `README.md` only for documentation items explicitly left by earlier steps and only if doing so does not mix final report content into runtime docs.

## 11. Rollback Plan
Revert only documentation files modified in this step. Do not revert code, env files, generated files, Docker data, or user changes.
