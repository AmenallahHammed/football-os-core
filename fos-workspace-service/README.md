# fos-workspace-service Documentation

## 1) Purpose

`fos-workspace-service` is the workspace execution backend in Football OS.

It handles:

1. Document workflows (initiate upload, confirm upload, read, archive)
2. Event workflows (create/update/list/archive, reminder scheduling)
3. Player profile aggregation (canonical player + workspace tabs)
4. Notification inbox projection and read-state APIs
5. Workspace search across documents and events
6. OnlyOffice integration for editor config and save callback

## 2) Runtime role in the backend

Typical runtime flow:

1. Requests enter workspace controllers (`document`, `event`, `profile`, `notification`, `search`, `onlyoffice`).
2. Services enforce business rules and call governance policy/canonical integrations.
3. Mongo repositories persist/read workspace aggregates.
4. Services emit FACT/AUDIT/ALERT signals through `FosKafkaProducer`.
5. Kafka notification consumer materializes inbox notifications.
6. Hourly scheduler scans upcoming events and triggers reminders.

Cross-cutting behavior:

- JWT resource-server auth when enabled
- Mongock migrations for collection/index lifecycle
- Mongo auditing for metadata fields
- Global exception mapping

## 3) Folder structure

```text
fos-workspace-service/
  pom.xml
  src/
    main/
      java/com/fos/workspace/
        WorkspaceApp.java
        config/
        db/migration/
        document/
        event/
        profile/
        notification/
        search/
        onlyoffice/
      resources/
        application.yml
    test/java/com/fos/workspace/
      document/
      event/
      profile/
      notification/
      search/
      onlyoffice/
```

## 4) Data/model interaction map

Owned workspace models (Mongo):

- `WorkspaceDocument` in `workspace_documents`
- `WorkspaceEvent` in `workspace_events`
- `WorkspaceNotification` in `workspace_notifications`

Embedded workspace value objects:

- `DocumentVersion`
- `AttendeeRef`
- `RequiredDocument`
- `TaskAssignment`

External model interactions:

- Canonical player/team identity via `CanonicalResolver` (`sdk-canonical`)
- Authorization decisions via `PolicyClient` (`sdk-policy`)
- Actor identity context via `FosSecurityContext` (`sdk-security`)
- Storage object operations via `StoragePort` (`sdk-storage`)

Event interactions around models:

- Emits workspace domain signals and audit signals
- Consumes workspace signals to project user notifications
- Integrates with governance audit stream through `KafkaTopics.AUDIT_ALL`

## 5) Package responsibilities

- `config`: Mongock runner configuration + global exception handler
- `db/migration`: Mongock change units for collection/index creation
- `document`: document aggregate, API contracts, repo, and service logic
- `event`: event aggregate, API contracts, repo, and service logic
- `event/application/reminder`: reminder strategy and scheduled runner
- `profile`: player profile aggregation from canonical + workspace docs
- `notification`: inbox model/repository/API/service and Kafka projection
- `search`: cross-collection workspace search API/service
- `onlyoffice`: editor config generation and save callback handling

## 6) Key files and what they do

- `fos-workspace-service/src/main/java/com/fos/workspace/WorkspaceApp.java`
  - Bootstraps workspace context with Mongo auditing, Mongock, scheduling, and security filter.

- `fos-workspace-service/src/main/resources/application.yml`
  - Configures server, MongoDB, Kafka, security, governance endpoints, storage provider, and OnlyOffice settings.

- `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`
  - Orchestrates document upload lifecycle, policy checks, versioning, archive, and signal emission.

- `fos-workspace-service/src/main/java/com/fos/workspace/document/domain/WorkspaceDocument.java`
  - Document aggregate with canonical refs, tags, and version history.

- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/EventService.java`
  - Event lifecycle orchestration, policy checks, and event signal emission.

- `fos-workspace-service/src/main/java/com/fos/workspace/event/domain/WorkspaceEvent.java`
  - Event aggregate with attendees, required docs, tasks, and reminder state.

- `fos-workspace-service/src/main/java/com/fos/workspace/event/application/reminder/EventReminderScheduler.java`
  - Hourly reminder scanner for upcoming events.

- `fos-workspace-service/src/main/java/com/fos/workspace/profile/application/PlayerProfileService.java`
  - Builds player profile response using canonical player data and policy-filtered document tabs.

- `fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java`
  - Consumes workspace topics and persists inbox notifications.

- `fos-workspace-service/src/main/java/com/fos/workspace/notification/application/NotificationService.java`
  - Handles per-actor notification listing and read transitions.

- `fos-workspace-service/src/main/java/com/fos/workspace/search/application/WorkspaceSearchService.java`
  - Performs document/event search with policy-based category filtering.

- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
  - Generates OnlyOffice editor config and signed token after policy checks.

- `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java`
  - Processes OnlyOffice callback and appends a new document version snapshot.

- `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration001CreateDocumentIndexes.java`
  - Creates primary document indexes.

- `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration002CreateEventIndexes.java`
  - Creates primary event/reminder indexes.

- `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration003AddPlayerProfileIndex.java`
  - Adds compound index for player profile query path.

- `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration004CreateNotificationIndexes.java`
  - Adds notification inbox/unread query indexes.

## 7) Integrations with other backend components

- MongoDB for workspace aggregates
- Kafka for workspace signals and notification projection input
- Governance policy endpoint for authorization decisions
- Governance canonical endpoint for canonical player/team lookup
- Storage provider adapters (MinIO/noop, with extension path for others)
- OnlyOffice Document Server for rich document editing flow

## 8) Practical mental model

Treat workspace service as the operational layer:

1. Manage daily workspace artifacts (docs/events)
2. Enforce policy per action and category
3. Aggregate and present profile/search views
4. Materialize notifications from event streams
5. Integrate document editing workflows

## 9) Quick Start / Run Locally

Prerequisites:

- Java 21
- Maven 3.9+
- MongoDB and Kafka running
- Governance service running (policy/canonical endpoints)

Useful optional dependencies for full flow:

- MinIO for object storage
- OnlyOffice Document Server for editor integration

Common environment variables:

- `MONGODB_URI`
- `KAFKA_BOOTSTRAP_SERVERS`
- `GOVERNANCE_URL`
- `STORAGE_PROVIDER` (`minio` or `noop`)
- `FOS_SECURITY_ENABLED` (`false` for local no-auth testing)

Run from repository root:

```bash
mvn -pl fos-workspace-service -am spring-boot:run
```

Run tests:

```bash
mvn -pl fos-workspace-service test
```
