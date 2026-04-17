# Agent Execution Guide
## Phase 1 — FOS Workspace

---

## Purpose

This guide governs how an AI agent must behave when executing Phase 1 sprints for the `fos-workspace-service` and `fos-workspace-frontend`. It defines document authority, execution rules, error reporting behavior, and hard gates between sprints.

Read this entire guide before touching any file.

---

## Document Authority

### Primary Source — Phase Documents

The phase documents are the **authoritative execution source**. The agent follows them exclusively for:

- what files to create or modify
- what code to write
- what commands to run
- what the expected output of each command is
- what commit message to use
- what order to execute steps in

Phase documents are:

```
2026-04-16-phase1-sprint01-workspace-scaffold-documents.md
2026-04-16-phase1-sprint02-workspace-calendar-events.md
2026-04-16-phase1-sprint03-workspace-profiles-medical-onlyoffice.md
2026-04-16-phase1-sprint04-workspace-notifications-search.md
2026-04-16-phase1-sprint05-workspace-angular-frontend.md
```

### Secondary Source — Plan Documents (Review Checklist Only)

The plan documents are **not execution instructions**. The agent uses them only after completing each sprint task or at sprint exit to:

- verify no constraint was violated
- catch version mismatches or version drift
- confirm nothing is out of scope
- check that all non-negotiable architecture rules were honored

Plan documents are:

```
sprint1_1-plan.md
sprint1_2-plan.md
sprint1_3-plan.md
sprint1_4-plan.md
sprint1_5-plan.md
```

> **Rule:** If a plan document and a phase document disagree, flag the conflict immediately and do not proceed until it is resolved. Do not silently pick one over the other.

---

## Sprint Execution Order

Sprints must be executed in strict sequence. No sprint may begin until the previous sprint's exit criteria are fully satisfied.

```
Sprint 1.1 → Sprint 1.2 → Sprint 1.3 → Sprint 1.4 → Sprint 1.5
```

Each sprint has a hard prerequisite gate defined in its phase document. The agent must verify the gate explicitly before writing any code for that sprint.

If a prerequisite is not satisfied, the agent must stop, state which prerequisite is missing, and wait for the user to confirm it is resolved.

**Skipping a sprint is never allowed, even if the user asks.**

---

## Step-Level Execution Rules

### Follow the phase document task by task

Each phase document is organized into numbered Tasks. Each Task has numbered Steps. The agent executes them in order, top to bottom, without reordering.

### Run every verification command

Every `bash` command block in the phase document must be run. If a command produces unexpected output, the agent must stop and report it before continuing to the next step.

### Match expected output exactly

Phase documents state expected output for commands. If the actual output does not match — different status, missing field, wrong port, wrong collection name — this is a blocker. The agent reports it and waits.

### Commit after every task

The phase documents include exact commit messages per task. The agent uses those exact messages. It does not batch multiple tasks into one commit or skip commits.

### Never write code outside the defined file map

Each sprint's phase document includes an annotated file map listing every file with `CREATE` or `MODIFY`. The agent does not create files that are not in that map without flagging it first.

---

## Architecture Non-Negotiables

These rules apply across all sprints. The agent enforces them on every file it creates or modifies. Violation of any of these is a hard stop — the agent must revert and report before continuing.

| Rule | What it means |
|---|---|
| `StoragePort` only | Never instantiate `MinioClient` directly. All storage goes through `StoragePort` from `sdk-storage`. |
| `PolicyClient` only | Never write `if (role.equals(...))` in service code. All permission checks go through `PolicyClient`. |
| `AbstractFosConsumer` only | Any Kafka consumer must extend this base class. No raw `@KafkaListener` replacements. |
| `BaseDocument` extension | Every Mongo entity must extend `BaseDocument` from `sdk-core`. |
| Mongock only for schema | No `spring.data.mongodb.auto-index-creation`. No manual index creation outside a Mongock changeset. |
| Spring Data MongoDB only | No raw `MongoClient` usage in domain or application layers. |
| SDK modules only | `fos-workspace-service` depends only on `fos-sdk` modules, never on `fos-governance-service` internals. |
| Soft delete only | `ARCHIVED` state, never physical document removal. |
| `CanonicalRef` for ownership | Owner, linked player, linked team references all use `CanonicalRef`. |
| Version `0.1.0-SNAPSHOT` | Root pom version is `0.1.0-SNAPSHOT`. Never use `1.0.0-SNAPSHOT`. |

---

## Known Errors to Watch For

The following issues are known from reviewing the documents. The agent must check for these proactively and report them to the user for manual correction.

### Version mismatch in Sprint 1.1 phase document

**Location:** `2026-04-16-phase1-sprint01-workspace-scaffold-documents.md`, Task 2, Step 1 — the `pom.xml` snippet.

**Error:** The snippet uses `<version>1.0.0-SNAPSHOT</version>` in the `<parent>` block.

**Correct value:** `0.1.0-SNAPSHOT`

**What the agent does:** Before writing `fos-workspace-service/pom.xml`, the agent substitutes the correct version and reports to the user:

```
⚠️  MANUAL CORRECTION APPLIED
File: fos-workspace-service/pom.xml
Line: <version> inside <parent>
Found in phase doc: 1.0.0-SNAPSHOT
Corrected to:       0.1.0-SNAPSHOT
Reason: sprint1_1-plan.md Non-Negotiable Constraints confirm repo version is 0.1.0-SNAPSHOT.
No action needed from you — correction was applied automatically.
Please verify the parent version in pom.xml before committing.
```

---

## Error Reporting Protocol

The agent distinguishes between three categories of issues.

### Category A — Blocker (agent stops)

A blocker is any situation where continuing would produce incorrect output or violate an architecture rule. The agent stops immediately, reports the issue in the format below, and waits for user input.

```
🛑 BLOCKER — Sprint X.Y, Task N, Step M
Issue:    [description of the problem]
Expected: [what the phase doc said would happen]
Actual:   [what actually happened]
Action:   Agent is stopped. Please resolve this and confirm to continue.
```

Examples of blockers:
- A prerequisite gate check fails
- A build that the phase doc says should succeed produces a failure
- A Mongo collection or index is missing after migration
- A required SDK bean cannot be wired

### Category B — Manual Fix Required (agent pauses)

A manual fix is an issue the agent cannot resolve itself — it requires user intervention outside the codebase, such as a Docker/environment issue, a Keycloak config, or a network-level problem.

```
🔧 MANUAL FIX REQUIRED — Sprint X.Y, Task N, Step M
Issue:    [description]
How to fix: [exact steps the user should take]
After fixing: confirm to the agent to continue.
```

Examples:
- Docker daemon not running
- Testcontainers cannot connect to Docker socket
- Keycloak realm not configured
- MinIO not reachable in compose stack

### Category C — Auto-Corrected (agent continues, user informed)

An auto-correction is a known, documented discrepancy — like the version mismatch above — where the correct value is unambiguous from other documents. The agent applies the fix and reports it but does not stop.

```
⚠️  AUTO-CORRECTION APPLIED — Sprint X.Y, Task N
File:     [file path]
Change:   [what was changed and why]
You should review this before committing.
```

---

## Sprint Exit Checklist

Before declaring a sprint complete, the agent runs through the corresponding plan document as a review checklist. It verifies:

- All items in "Definition of Done" are satisfied
- All items in "Strict Acceptance Checklist" are satisfied
- No items from "Out of Scope" were accidentally implemented
- All "Non-Negotiable Constraints" were honored
- The root build still passes

If any checklist item fails, the sprint is not complete. The agent reports which items are outstanding.

---

## Out of Scope Enforcement

Each sprint has an explicit "Out of Scope" section in both the phase and plan documents. The agent must not implement anything listed there, even if the user asks informally mid-sprint.

If a user asks for something out of scope during a sprint, the agent responds:

```
⚠️  OUT OF SCOPE FOR THIS SPRINT
You asked for: [feature]
This is scheduled for: Sprint X.Y
Proceeding with current sprint as planned.
If you want to re-prioritize, please confirm and we will re-plan together.
```

---

## Testcontainers / Docker Local Risk

All sprints note that Testcontainers may fail locally due to Docker named pipe issues. This is a **known environment risk, not a code bug**.

If integration tests fail due to Docker/Testcontainers:

1. The agent reports it as Category B (Manual Fix Required)
2. The agent documents which tests failed and why the failure is environment-only
3. The sprint is not blocked from proceeding past tests if the failure is confirmed environment-only by the user
4. The agent records the open item in the sprint exit report

---

## Summary — Agent Decision Tree

```
Start sprint
    │
    ▼
Check prerequisite gate
    │
    ├── Gate fails → STOP, report blocker, wait for user
    │
    └── Gate passes
            │
            ▼
        Execute phase document task by task
            │
            ├── Known error encountered → apply auto-correction, inform user, continue
            │
            ├── Unexpected blocker → STOP, report, wait
            │
            ├── Manual environment issue → pause, report fix steps, wait
            │
            └── Step succeeds → commit per phase doc, continue to next step
                    │
                    ▼
                All tasks done
                    │
                    ▼
                Run plan document exit checklist
                    │
                    ├── Items outstanding → report, do not declare sprint complete
                    │
                    └── All pass → declare sprint complete, state next sprint gate
```

---

## Quick Reference

| What to follow for implementation | Phase documents |
|---|---|
| What to use for constraint review | Plan documents |
| Sprint order | 1.1 → 1.2 → 1.3 → 1.4 → 1.5, no skipping |
| On conflict between docs | Stop and flag, never silently decide |
| On architecture violation | Stop, revert, report |
| On known version bug in Sprint 1.1 phase doc | Auto-correct to `0.1.0-SNAPSHOT` and inform user |
| On Testcontainers failure | Report as environment issue, continue with user confirmation |
| On out-of-scope request | Decline, name the correct sprint, continue |
