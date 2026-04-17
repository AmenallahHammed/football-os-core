# Sprint 1.4 Plan
## Phase 1 - Notifications + Search + OnlyOffice Save Callback

## Goal
Extend `fos-workspace-service` with:
- notification inbox persistence and APIs
- Kafka consumer for workspace signals
- workspace search endpoint
- OnlyOffice save callback handling
- notification indexes via Mongock

This sprint closes the async loop started in earlier sprints:
- reminders and upload signals become inbox notifications
- OnlyOffice edits create new document versions
- search spans documents and events

## Hard Prerequisite Gate
Sprint 1.4 must not start until:
- [ ] Sprint 1.1 document flow exists
- [ ] Sprint 1.2 events and reminder topics exist
- [ ] Sprint 1.3 OnlyOffice config endpoint and runtime config exist
- [ ] workspace service boots and connects to Mongo/Kafka
- [ ] workspace tests are already in place

## Definition of Done
- [ ] notification domain exists in Mongo
- [ ] `Migration004CreateNotificationIndexes` runs successfully
- [ ] workspace Kafka consumer extends `AbstractFosConsumer`
- [ ] notifications are written from relevant ALERT/FACT signals
- [ ] notification inbox endpoints work
- [ ] unread count works
- [ ] mark-read and mark-all-read work
- [ ] search endpoint returns documents and events filtered by permission
- [ ] OnlyOffice save callback creates a new document version flow or an explicitly documented Phase 1-safe partial implementation
- [ ] integration tests cover notifications and search

## Non-Negotiable Constraints
- [ ] notification inbox is pull-based, not websocket push
- [ ] notifications are append-only and marked read, not deleted
- [ ] consumer must use `AbstractFosConsumer`
- [ ] no raw ad-hoc `@KafkaListener` implementation replacing the template method base
- [ ] search remains simple Mongo-based Phase 1 search
- [ ] search must filter by permissions
- [ ] storage interactions still go only through `StoragePort`
- [ ] OnlyOffice callback must not corrupt version history
- [ ] no OpenSearch implementation in this sprint

## Current Repo Alignment Notes
- [ ] current `StoragePort` does not support direct byte upload
- [ ] phase doc itself defers direct upload support beyond this sprint in one area
- [ ] local Docker/Testcontainers remains a risk for tests
- [ ] notification consumer depends on topic names established in previous sprints
- [ ] workspace service foundation is still a prerequisite

## Deliverables
- [ ] `NotificationType`
- [ ] `WorkspaceNotification`
- [ ] `WorkspaceNotificationRepository`
- [ ] `Migration004CreateNotificationIndexes`
- [ ] `WorkspaceKafkaConsumer`
- [ ] `NotificationResponse`
- [ ] `NotificationService`
- [ ] `NotificationController`
- [ ] `SearchResponse`
- [ ] `WorkspaceSearchService`
- [ ] `SearchController`
- [ ] `OnlyOfficeSaveHandler`
- [ ] callback wiring in `OnlyOfficeController`
- [ ] `NotificationIntegrationTest`
- [ ] `SearchIntegrationTest`

## Execution Order

### 0. Prerequisite Validation
- [ ] confirm sprints 1.1-1.3 foundation exists
- [ ] confirm notification-producing topics exist or are at least defined
- [ ] confirm workspace app has Kafka wiring
- [ ] confirm OnlyOffice config/controller already exists before adding callback logic
- [ ] stop if these prerequisites are missing

### 1. Create Notification Domain + Migration
- [ ] create `NotificationType`
- [ ] create `WorkspaceNotification`
- [ ] create `WorkspaceNotificationRepository`
- [ ] create `Migration004CreateNotificationIndexes`

### 1.1 Notification Domain Rules
- [ ] notification extends `BaseDocument`
- [ ] notification has recipient actor id
- [ ] notification has optional triggering actor id
- [ ] notification stores type/title/body
- [ ] notification stores read flag
- [ ] notification may link related document/event ids
- [ ] notifications are active immediately
- [ ] mark-read behavior is encapsulated in domain

### 1.2 Migration Rules
- [ ] create `workspace_notifications` collection if missing
- [ ] index recipient + createdAt
- [ ] index recipient + read
- [ ] keep migration order `004`
- [ ] rollback is complete

### 2. Implement Kafka Consumer
- [ ] create `WorkspaceKafkaConsumer`
- [ ] extend `AbstractFosConsumer`
- [ ] subscribe to:
- [ ] `fos.workspace.event.document.missing`
- [ ] `fos.workspace.document.uploaded`
- [ ] map signal types/topics to notification records
- [ ] persist notifications via repository

### 2.1 Consumer Rules
- [ ] handle only domain logic in subclass
- [ ] let base class own deserialization/MDC/DLQ behavior
- [ ] support both ALERT and FACT style messages as needed
- [ ] make topic-to-notification mapping explicit
- [ ] avoid duplicate notification creation for the same signal if replay can happen
- [ ] log enough context for diagnosis

### 3. Implement Notification API
- [ ] create `NotificationResponse`
- [ ] create `NotificationService`
- [ ] create `NotificationController`

### 3.1 Notification Service Rules
- [ ] fetch notifications for current actor from `FosSecurityContext`
- [ ] support unread-only listing
- [ ] support unread count
- [ ] support mark single as read
- [ ] support mark all as read
- [ ] block actors from marking others' notifications as read

### 3.2 Notification API Rules
- [ ] `GET /api/v1/notifications`
- [ ] `GET /api/v1/notifications/unread-count`
- [ ] `PATCH /api/v1/notifications/{id}/read`
- [ ] `POST /api/v1/notifications/mark-all-read`
- [ ] use pagination on list endpoint

### 4. Implement Search
- [ ] create `SearchResponse`
- [ ] create `WorkspaceSearchService`
- [ ] create `SearchController`

### 4.1 Search Rules
- [ ] search documents by regex/name through repository
- [ ] filter document results by category permission via `PolicyClient`
- [ ] search events by title for Phase 1 simplicity
- [ ] return both lists in one response
- [ ] cap search result count
- [ ] generate document download URLs via `StoragePort`
- [ ] do not expose unauthorized categories/results

### 4.2 Search Design Checks
- [ ] if a Specification helper is introduced, keep it focused and optional
- [ ] do not introduce OpenSearch yet
- [ ] do not bypass policy checks for convenience
- [ ] keep search endpoint read-only and side-effect free

### 5. Implement OnlyOffice Save Callback
- [ ] create `OnlyOfficeSaveHandler`
- [ ] wire `OnlyOfficeController` callback endpoint to handler

### 5.1 Callback Rules
- [ ] parse OnlyOffice callback JSON safely
- [ ] ignore statuses that are not save-worthy
- [ ] handle statuses `2` and `6` as save events
- [ ] load target document by `resourceId`
- [ ] download updated bytes from OnlyOffice callback URL
- [ ] create new document version metadata
- [ ] persist new version on document
- [ ] emit AUDIT or FACT as appropriate
- [ ] always return callback ack body expected by OnlyOffice

### 5.2 Phase 1 Practical Constraint
- [ ] current `StoragePort` lacks direct put/upload-from-bytes support
- [ ] decide explicitly before implementation:
- [ ] temporary partial implementation with documented limitation
- [ ] or extend storage abstraction in a separate approved change
- [ ] do not hide this limitation silently

### 6. Add Integration Tests
- [ ] create `NotificationIntegrationTest`
- [ ] create `SearchIntegrationTest`

### 6.1 Notification Test Coverage
- [ ] list notifications returns `200`
- [ ] unread count returns `count`
- [ ] mark-read changes persisted state
- [ ] mark-all-read works
- [ ] actor ownership rules are respected if feasible

### 6.2 Search Test Coverage
- [ ] searchable document is returned
- [ ] empty/no-match path works
- [ ] role-filtered category visibility is preserved if feasible
- [ ] event search path is covered if event data is available

### 6.3 Callback Test Consideration
- [ ] if callback integration is testable in this sprint, add focused tests
- [ ] otherwise document why callback test is deferred and what is covered manually

### 7. Verification
- [ ] run workspace tests
- [ ] verify `Migration004` in Mongock changelog
- [ ] produce at least one notification from a consumed signal
- [ ] manually check inbox endpoints
- [ ] manually check search endpoint
- [ ] manually exercise OnlyOffice callback with sample payload

## Strict Acceptance Checklist
- [ ] notification collection and indexes exist
- [ ] `WorkspaceKafkaConsumer` extends `AbstractFosConsumer`
- [ ] consumed document/event signals create notifications
- [ ] inbox list endpoint works
- [ ] unread count endpoint works
- [ ] mark-read endpoint works
- [ ] mark-all-read endpoint works
- [ ] search returns documents/events
- [ ] search results are permission-filtered
- [ ] OnlyOffice callback path is wired and not stub-only
- [ ] document version history remains valid after save handling
- [ ] no architecture violations introduced

## Risks
- [ ] signal topic contracts may drift from earlier sprint implementations
- [ ] duplicate notification creation if replay/idempotency is ignored
- [ ] search can accidentally leak unauthorized categories
- [ ] `StoragePort` capability gap can block full callback completion
- [ ] local Kafka/Testcontainers/Docker instability may affect tests

## Risk Mitigations
- [ ] verify topic names before consumer implementation
- [ ] make notification mapping deterministic
- [ ] enforce policy checks before returning search results
- [ ] explicitly document callback limitation if storage byte upload is not added
- [ ] keep callback failure non-fatal to OnlyOffice response contract

## Suggested Commit Boundaries
- [ ] commit 1: notification domain + migration
- [ ] commit 2: consumer
- [ ] commit 3: notification service/controller
- [ ] commit 4: search service/controller
- [ ] commit 5: OnlyOffice save handler + controller callback
- [ ] commit 6: integration tests
- [ ] commit 7: verification and cleanup

## Out of Scope
- [ ] websocket notifications
- [ ] notification preferences
- [ ] OpenSearch/Elasticsearch
- [ ] advanced relevance ranking
- [ ] full storage abstraction redesign unless separately approved
- [ ] frontend inbox/search UI polish

## Exit Criteria
Sprint 1.4 is complete only when:
- [ ] notification persistence and APIs work
- [ ] consumer uses template-method base class
- [ ] search works with permission filtering
- [ ] callback path is meaningfully implemented and documented
- [ ] migration runs cleanly
- [ ] tests pass or only fail for documented environment reasons
