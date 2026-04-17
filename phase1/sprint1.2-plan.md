# Sprint 1.2 Plan
## Phase 1 - Workspace Calendar & Event Management

## Goal
Deliver the `event` package inside `fos-workspace-service` with:
- create event
- update event
- get event
- list events by team
- soft-delete event
- scheduled reminder scanning
- ALERT signal emission for missing required documents
- Mongock migration for `workspace_events`

All permission checks must go through `PolicyClient`.
All state-changing operations must emit Kafka signals through `FosKafkaProducer`.

## Hard Prerequisite Gate
Sprint 1.2 must not start implementation until Sprint 1.1 is materially complete.

Current repo status:
- [ ] `fos-workspace-service` does not exist yet
- [ ] root `pom.xml` does not include `fos-workspace-service`
- [ ] workspace gateway routes do not exist
- [ ] workspace OPA policy file does not exist yet

Therefore Sprint 1.2 execution is blocked until Sprint 1.1 foundation exists.

## Foundation Required Before Starting
- [ ] `fos-workspace-service` module exists
- [ ] module builds from root
- [ ] Mongo + Mongock wiring is working
- [ ] workspace app starts on `8082`
- [ ] `workspace_policy.rego` exists
- [ ] document package from Sprint 1.1 exists
- [ ] workspace test infrastructure is in place

## Definition of Done
- [ ] event domain model exists and is persisted in MongoDB
- [ ] `Migration002CreateEventIndexes` runs successfully
- [ ] `WorkspaceEventRepository` exists with scheduler-supporting query methods
- [ ] reminder strategy pattern is implemented
- [ ] scheduler scans upcoming events and emits ALERT signals for missing required documents
- [ ] event CRUD endpoints work
- [ ] list-by-team endpoint works
- [ ] event policy rules exist and match service action names
- [ ] integration tests cover create, update, delete, list
- [ ] all workspace tests pass or remaining failures are explicitly documented as environment-only

## Non-Negotiable Constraints
- [ ] event code lives under `fos-workspace-service`
- [ ] use the same layering as Sprint 1.1: `api -> application -> domain -> infrastructure/persistence`
- [ ] `WorkspaceEvent` extends `BaseDocument`
- [ ] all persistence is through Spring Data MongoDB
- [ ] all collection/index creation is through Mongock
- [ ] all permission checks are through `PolicyClient`
- [ ] no direct role checks in service logic
- [ ] all state-changing operations emit signals
- [ ] scheduler remains simple Spring scheduling, not Quartz or custom job infra
- [ ] reminder logic is isolated behind `ReminderStrategy`
- [ ] attendees, required documents, and tasks remain embedded in one Mongo document
- [ ] no separate collections for attendees/tasks/requirements in this sprint

## Current Repo Alignment Notes
- [ ] repo version is `0.1.0-SNAPSHOT`
- [ ] Testcontainers remains a known local runtime risk
- [ ] gateway currently routes only governance endpoints
- [ ] workspace service is not yet scaffolded
- [ ] policy endpoint semantics were already updated in governance; workspace policies must align with existing OPA loading model

## Deliverables
- [ ] `EventType`
- [ ] `AttendeeRef`
- [ ] `RequiredDocument`
- [ ] `TaskAssignment`
- [ ] `WorkspaceEvent`
- [ ] `Migration002CreateEventIndexes`
- [ ] `WorkspaceEventRepository`
- [ ] `ReminderStrategy`
- [ ] `DocumentMissingReminderStrategy`
- [ ] `EventReminderScheduler`
- [ ] `CreateEventRequest`
- [ ] `UpdateEventRequest`
- [ ] `EventResponse`
- [ ] `EventService`
- [ ] `EventController`
- [ ] workspace event rules appended to `workspace_policy.rego`
- [ ] `EventIntegrationTest`

## Execution Order

### 0. Prerequisite Validation
- [ ] confirm Sprint 1.1 module exists
- [ ] confirm workspace app boots
- [ ] confirm workspace Mongo migrations work
- [ ] confirm workspace tests run at least to compile level
- [ ] if any of the above is false, stop and complete Sprint 1.1 first

### 1. Create Event Domain Model
- [ ] create `EventType`
- [ ] create `AttendeeRef`
- [ ] create `RequiredDocument`
- [ ] create `TaskAssignment`
- [ ] create `WorkspaceEvent`

### 1.1 Domain Rules To Implement
- [ ] event has title, description, type, start/end, location
- [ ] event has `createdByRef`
- [ ] event optionally has `teamRef`
- [ ] event has attendees collection
- [ ] event has required-documents collection
- [ ] event has tasks collection
- [ ] event tracks `reminderSent`
- [ ] event uses `ACTIVE/ARCHIVED` lifecycle
- [ ] `softDelete()` archives the event
- [ ] `hasMissingDocuments()` is implemented in the aggregate
- [ ] mutator methods exist for add/update embedded data where needed
- [ ] guard invalid temporal state:
- [ ] `endAt` must be after `startAt`
- [ ] task due dates should not be nonsensical relative to event timing
- [ ] reminder flag can be marked once emitted

### 1.2 Domain Design Checks
- [ ] `AttendeeRef` uses `CanonicalRef`
- [ ] `RequiredDocument` tracks submission state by linked document id
- [ ] `TaskAssignment` supports completion state
- [ ] aggregate methods encapsulate behavior instead of controller/service mutating raw lists directly where possible

### 2. Add Migration002
- [ ] create `Migration002CreateEventIndexes`
- [ ] create collection `workspace_events` if missing
- [ ] add indexes for:
- [ ] `teamRef.id`
- [ ] `startAt`
- [ ] `createdByRef.id`
- [ ] `type`
- [ ] `state`
- [ ] compound reminder query index on `state + reminderSent + startAt`
- [ ] verify migration ordering is `002`
- [ ] keep rollback implementation complete

### 3. Create Repository
- [ ] create `WorkspaceEventRepository`
- [ ] add `findByResourceId`
- [ ] add team-based listing query
- [ ] add type-based listing query
- [ ] add created-by query if actually needed
- [ ] add scheduler query for upcoming events needing reminder
- [ ] ensure query methods match Mongo field names exactly

### 4. Implement Reminder Strategy Layer
- [ ] create `ReminderStrategy`
- [ ] create `DocumentMissingReminderStrategy`
- [ ] create `EventReminderScheduler`

### 4.1 Reminder Behavior Rules
- [ ] scheduler runs every hour
- [ ] scheduler queries upcoming ACTIVE events within next 24 hours
- [ ] scheduler skips events already marked `reminderSent`
- [ ] strategy checks whether event truly has missing required documents
- [ ] one ALERT signal per missing requirement assignee
- [ ] event gets marked as reminded after successful emission
- [ ] scheduler persists reminder state change
- [ ] scheduler does not emit duplicate reminders every run

### 4.2 Signal Rules
- [ ] use `SignalType.ALERT` for reminder notifications
- [ ] topic name must be stable and documented
- [ ] signal actor/ref semantics should be consistent with workspace notification consumer planned for Sprint 1.4
- [ ] include useful payload if current signal model supports it cleanly

### 5. Create API DTOs
- [ ] create `CreateEventRequest`
- [ ] create `UpdateEventRequest`
- [ ] create `EventResponse`

### 5.1 DTO Validation Rules
- [ ] `title` required on create
- [ ] `type` required on create
- [ ] `startAt` required on create
- [ ] `endAt` required on create
- [ ] reject invalid empty attendee/task/document payloads where necessary
- [ ] avoid exposing internal Mongo ids
- [ ] use `resourceId` as public event id

### 6. Implement EventService
- [ ] create `EventService`
- [ ] add `createEvent`
- [ ] add `getEvent`
- [ ] add `listEventsByTeam`
- [ ] add `updateEvent`
- [ ] add `deleteEvent`

### 6.1 Service Rules
- [ ] current actor resolved via `FosSecurityContext`
- [ ] permission checks:
- [ ] `workspace.event.create`
- [ ] `workspace.event.read`
- [ ] `workspace.event.update`
- [ ] `workspace.event.delete`
- [ ] create event with `createdByRef`
- [ ] map attendee inputs to `CanonicalRef`
- [ ] map required-document inputs
- [ ] map task inputs
- [ ] persist event
- [ ] emit FACT on create
- [ ] emit FACT on update
- [ ] emit AUDIT or FACT on delete according to chosen eventing convention
- [ ] soft-delete only, never physical delete

### 6.2 Service Review Requirements
- [ ] do not skip read permission checks on `getEvent`
- [ ] do not skip read permission checks on list operations
- [ ] do not mutate aggregate fields directly if aggregate methods exist
- [ ] validate temporal updates on edit
- [ ] avoid partial update bugs that create invalid event ranges

### 7. Create EventController
- [ ] create `EventController`
- [ ] expose:
- [ ] `POST /api/v1/events`
- [ ] `GET /api/v1/events/{eventId}`
- [ ] `GET /api/v1/events?teamRefId=...`
- [ ] `PUT /api/v1/events/{eventId}`
- [ ] `DELETE /api/v1/events/{eventId}`
- [ ] return `201` on create
- [ ] return `200` on get/update/list
- [ ] return `204` on delete
- [ ] keep controller thin and delegate all logic to service

### 8. Extend OPA Workspace Policies
- [ ] append event rules to `fos-governance-service/src/main/resources/opa/workspace_policy.rego`
- [ ] allow create/update/delete for:
- [ ] `ROLE_HEAD_COACH`
- [ ] `ROLE_CLUB_ADMIN`
- [ ] allow read for coaching staff roles
- [ ] ensure role sets align with existing workspace/general coaching role definitions
- [ ] verify action strings match service exactly

### 9. Add Integration Tests
- [ ] create `EventIntegrationTest`
- [ ] cover:
- [ ] create returns `201`
- [ ] update modifies persisted fields
- [ ] delete archives event
- [ ] list-by-team returns `200`
- [ ] wire `PolicyClient` to WireMock allow responses
- [ ] use existing workspace test infrastructure
- [ ] if feasible, add one scheduler-focused test:
- [ ] event within reminder window
- [ ] missing documents present
- [ ] ALERT signal emitted
- [ ] `reminderSent` updated

### 10. Verification
- [ ] run workspace module tests
- [ ] run root package/build
- [ ] verify `Migration002` appears in Mongock changelog
- [ ] boot workspace service and check health
- [ ] smoke test event endpoints manually
- [ ] verify scheduler bean is active
- [ ] verify reminder logic with controlled test data

## Strict Acceptance Checklist
- [ ] `POST /api/v1/events` returns `201`
- [ ] created event persists as `ACTIVE`
- [ ] `PUT /api/v1/events/{id}` updates allowed fields
- [ ] `DELETE /api/v1/events/{id}` archives event
- [ ] `GET /api/v1/events/{id}` returns event payload
- [ ] `GET /api/v1/events?teamRefId=...` returns paginated events
- [ ] `Migration002CreateEventIndexes` ran successfully
- [ ] scheduler query index exists
- [ ] `EventReminderScheduler` is wired
- [ ] missing-document reminders emit ALERT signals
- [ ] duplicate reminder spam is prevented
- [ ] no architecture violations introduced

## Dependencies On Sprint 1.1 Artifacts
- [ ] workspace module structure
- [ ] Mongo + Mongock bootstrap
- [ ] workspace exception handling
- [ ] workspace app config
- [ ] policy wiring
- [ ] Kafka producer wiring
- [ ] gateway route conventions
- [ ] test setup conventions

## Risks
- [ ] Sprint 1.2 is blocked until Sprint 1.1 exists
- [ ] local Testcontainers may still fail because of Docker pipe issues
- [ ] scheduler behavior is easy to make flaky in tests
- [ ] repeated reminders may occur if `reminderSent` persistence is mishandled
- [ ] event read permission is easy to omit because create/update/delete are more obvious
- [ ] DTO snippets in the phase doc may need repo-specific adaptation
- [ ] signal topic naming can drift from future notification consumer assumptions

## Risk Mitigations
- [ ] refuse to start Sprint 1.2 implementation before Sprint 1.1 bootstraps
- [ ] add compile/build checkpoints after each layer
- [ ] test scheduler logic separately from full cron timing where possible
- [ ] encapsulate reminder decision logic in strategy, not scheduler loop
- [ ] validate policy action names centrally before writing tests
- [ ] persist and assert `reminderSent` state explicitly

## Suggested Commit Boundaries
- [ ] commit 1: event domain model
- [ ] commit 2: migration002 + repository
- [ ] commit 3: reminder strategy + scheduler
- [ ] commit 4: DTOs + service + controller
- [ ] commit 5: OPA rules
- [ ] commit 6: integration tests
- [ ] commit 7: verification and cleanup

## Out of Scope
- [ ] notification inbox persistence
- [ ] search endpoint
- [ ] OnlyOffice
- [ ] player profiles
- [ ] medical/admin specialized document behavior
- [ ] Angular UI
- [ ] Quartz or advanced scheduling infrastructure
- [ ] Kafka Streams time-window reminder processing

## Exit Criteria
Sprint 1.2 is complete only when:
- [ ] Sprint 1.1 foundation exists and is stable
- [ ] workspace event CRUD is working
- [ ] reminder scheduling is implemented and verifiable
- [ ] OPA event permissions are loaded and used
- [ ] integration tests pass or only fail for explicitly documented environment issues
- [ ] root build remains healthy
