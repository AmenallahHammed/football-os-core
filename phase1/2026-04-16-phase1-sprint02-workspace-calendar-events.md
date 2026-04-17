# Phase 1 Sprint 1.2 — fos-workspace-service: Calendar & Event Management

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The `event` package inside `fos-workspace-service` is fully implemented. A Head Coach can create, edit, delete, and list events. Events have attendees (CanonicalRef list of players/staff), required documents (list of WorkspaceDocument IDs), task assignments, and scheduled reminders. Reminders are emitted as ALERT signals to Kafka when a document has not been submitted 24 hours before the event. A Mongock migration creates the `workspace_events` collection and its indexes. Integration tests confirm CRUD and signal emission.

**Architecture:**
The `event` package follows the same layered structure as the `document` package from Sprint 1.1: `api → application → domain → infrastructure/persistence`. All permission checks go through `PolicyClient`. All state-changing operations emit signals via `FosKafkaProducer`. The reminder system is a Spring `@Scheduled` task that runs every hour, queries for upcoming events with missing documents, and emits ALERT signals. We do NOT build a heavyweight scheduler (Quartz, etc.) — a simple cron is sufficient for Phase 1.

**Why scheduled task instead of an event-driven approach?**
For Phase 1, a scheduled task that polls the database every hour is simpler, testable, and sufficient. In Phase 2+ we can replace it with a Kafka Streams time-window processor if volume demands it. Do not over-engineer what is not yet needed.

**Tech Stack:** Java 21, Spring Boot 3.3.x, MongoDB 7, Mongock 5.4.x, Spring Scheduling (`@Scheduled`), `sdk-events` (FosKafkaProducer, KafkaTopics), `sdk-policy` (PolicyClient), `sdk-canonical` (CanonicalRef), JUnit 5, Testcontainers, WireMock

**Required Patterns This Sprint:**
- `[REQUIRED]` **Proxy (Remote)** — `PolicyClient` for all permission checks
- `[REQUIRED]` **Observer (via Kafka)** — ALERT signals emitted by the reminder scheduler; notification consumers (Sprint 1.4) subscribe to these signals
- `[RECOMMENDED]` **Strategy** — `ReminderStrategy` interface with a `DocumentMissingReminderStrategy` implementation; makes reminder logic swappable/testable

---

## File Map

```
fos-workspace-service/
└── src/
    ├── main/java/com/fos/workspace/
    │   ├── event/
    │   │   ├── api/
    │   │   │   ├── EventController.java                               CREATE
    │   │   │   ├── CreateEventRequest.java                            CREATE
    │   │   │   ├── UpdateEventRequest.java                            CREATE
    │   │   │   └── EventResponse.java                                 CREATE
    │   │   ├── application/
    │   │   │   ├── EventService.java                                  CREATE
    │   │   │   └── reminder/
    │   │   │       ├── ReminderStrategy.java                          CREATE
    │   │   │       ├── DocumentMissingReminderStrategy.java           CREATE
    │   │   │       └── EventReminderScheduler.java                    CREATE
    │   │   ├── domain/
    │   │   │   ├── WorkspaceEvent.java                                CREATE
    │   │   │   ├── EventType.java                                     CREATE
    │   │   │   ├── AttendeeRef.java                                   CREATE
    │   │   │   ├── RequiredDocument.java                              CREATE
    │   │   │   └── TaskAssignment.java                                CREATE
    │   │   └── infrastructure/
    │   │       └── persistence/
    │   │           └── WorkspaceEventRepository.java                  CREATE
    │   └── db/migration/
    │       └── Migration002CreateEventIndexes.java                    CREATE
    └── test/java/com/fos/workspace/
        └── event/
            └── EventIntegrationTest.java                              CREATE
```

---

## Task 1: Event Domain Model

**Why:** Before we write any service logic, we define exactly what a "calendar event" looks like in our domain. This is the most important step — all the service and API code flows naturally from a well-designed domain model.

**Key design decisions:**
- An event has a list of `AttendeeRef` — CanonicalRef wrappers for players and staff. We store refs, not names. Names are resolved at display time via CanonicalResolver.
- An event has a list of `RequiredDocument` — each entry specifies a document type/description that attendees must provide before the event.
- An event has a list of `TaskAssignment` — specific tasks assigned to specific actors with due dates.
- All these are embedded as arrays inside the `WorkspaceEvent` MongoDB document — no separate collections.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/domain/EventType.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/domain/AttendeeRef.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/domain/RequiredDocument.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/domain/TaskAssignment.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/domain/WorkspaceEvent.java`

- [ ] **Step 1: Create EventType enum**

```java
// EventType.java
package com.fos.workspace.event.domain;

/**
 * What kind of calendar event this is.
 * The type controls which roles can create/view it and
 * what reminder rules apply.
 */
public enum EventType {
    TRAINING,         // Regular training session
    MATCH,            // Official match
    MEETING,          // Team meeting
    MEDICAL_CHECK,    // Medical appointment
    ADMINISTRATIVE,   // Admin-only event (contract signing, etc.)
    OTHER
}
```

- [ ] **Step 2: Create AttendeeRef value object**

```java
// AttendeeRef.java
package com.fos.workspace.event.domain;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;

import java.util.UUID;

/**
 * An attendee of an event.
 * Wraps a CanonicalRef (pointing to a player or staff actor)
 * plus a flag indicating whether attendance is mandatory.
 *
 * We store the CanonicalRef, not the name. The frontend
 * resolves the display name via CanonicalResolver.
 */
public class AttendeeRef {

    private CanonicalRef canonicalRef;  // type = PLAYER or CLUB (staff)
    private boolean mandatory;          // true = must attend; false = optional
    private boolean confirmed;          // true = actor confirmed attendance

    protected AttendeeRef() {}

    public AttendeeRef(CanonicalRef canonicalRef, boolean mandatory) {
        this.canonicalRef = canonicalRef;
        this.mandatory = mandatory;
        this.confirmed = false;
    }

    public static AttendeeRef mandatoryPlayer(UUID playerId) {
        return new AttendeeRef(CanonicalRef.of(CanonicalType.PLAYER, playerId), true);
    }

    public static AttendeeRef optionalStaff(UUID staffActorId) {
        return new AttendeeRef(CanonicalRef.of(CanonicalType.CLUB, staffActorId), false);
    }

    public void confirm() { this.confirmed = true; }

    public CanonicalRef getCanonicalRef() { return canonicalRef; }
    public boolean isMandatory()          { return mandatory; }
    public boolean isConfirmed()          { return confirmed; }
}
```

- [ ] **Step 3: Create RequiredDocument value object**

```java
// RequiredDocument.java
package com.fos.workspace.event.domain;

import java.util.UUID;

/**
 * A document that must be submitted before an event.
 * For example: "Players must upload their pre-match medical clearance form
 * at least 24 hours before a MATCH event."
 *
 * submittedDocumentId is null until the actor actually uploads the document
 * and links it to this event requirement.
 */
public class RequiredDocument {

    private UUID requirementId;         // unique ID for this requirement entry
    private String description;         // human-readable: "Medical clearance form"
    private String documentCategory;    // "MEDICAL", "ADMIN", etc.
    private UUID assignedToActorId;     // which actor is responsible for submitting
    private UUID submittedDocumentId;   // null until submitted; set when actor links a document
    private boolean submitted;

    protected RequiredDocument() {}

    public RequiredDocument(String description, String documentCategory,
                             UUID assignedToActorId) {
        this.requirementId = UUID.randomUUID();
        this.description = description;
        this.documentCategory = documentCategory;
        this.assignedToActorId = assignedToActorId;
        this.submitted = false;
    }

    public void markSubmitted(UUID documentId) {
        this.submittedDocumentId = documentId;
        this.submitted = true;
    }

    public UUID getRequirementId()        { return requirementId; }
    public String getDescription()        { return description; }
    public String getDocumentCategory()   { return documentCategory; }
    public UUID getAssignedToActorId()    { return assignedToActorId; }
    public UUID getSubmittedDocumentId()  { return submittedDocumentId; }
    public boolean isSubmitted()          { return submitted; }
}
```

- [ ] **Step 4: Create TaskAssignment value object**

```java
// TaskAssignment.java
package com.fos.workspace.event.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A task assigned to a specific actor as part of an event.
 * Example: "Head Coach assigns Physio to prepare warm-up plan by 18:00".
 *
 * Not to be confused with RequiredDocument — tasks are general to-dos,
 * documents are specific file submissions.
 */
public class TaskAssignment {

    private UUID taskId;
    private String title;
    private String description;
    private UUID assignedToActorId;
    private Instant dueAt;              // when this task must be completed
    private boolean completed;
    private Instant completedAt;

    protected TaskAssignment() {}

    public TaskAssignment(String title, String description,
                          UUID assignedToActorId, Instant dueAt) {
        this.taskId = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.assignedToActorId = assignedToActorId;
        this.dueAt = dueAt;
        this.completed = false;
    }

    public void complete() {
        this.completed = true;
        this.completedAt = Instant.now();
    }

    public UUID getTaskId()               { return taskId; }
    public String getTitle()              { return title; }
    public String getDescription()        { return description; }
    public UUID getAssignedToActorId()    { return assignedToActorId; }
    public Instant getDueAt()             { return dueAt; }
    public boolean isCompleted()          { return completed; }
    public Instant getCompletedAt()       { return completedAt; }
}
```

- [ ] **Step 5: Create WorkspaceEvent entity**

```java
// WorkspaceEvent.java
package com.fos.workspace.event.domain;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.BaseDocument;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A calendar event in the workspace.
 *
 * Extends BaseDocument (sdk-core) → gives us id, resourceId, state,
 * version, createdAt, updatedAt for free.
 *
 * KEY DESIGN: attendees, requiredDocuments, and tasks are ALL
 * embedded arrays inside this document. There are no separate
 * collections. One MongoDB query loads the whole event.
 */
@Document(collection = "workspace_events")
public class WorkspaceEvent extends BaseDocument {

    private String title;
    private String description;
    private EventType type;
    private Instant startAt;            // when the event begins
    private Instant endAt;              // when the event ends
    private String location;            // optional: "Training Ground Field A"

    /**
     * The Head Coach who created this event.
     * CanonicalType.CLUB because actors (staff) are typed as CLUB members.
     */
    private CanonicalRef createdByRef;

    /**
     * Optional: the team this event is for.
     * CanonicalType.TEAM.
     */
    private CanonicalRef teamRef;

    /** All attendees: players + staff */
    private List<AttendeeRef> attendees = new ArrayList<>();

    /** Documents that must be submitted before this event */
    private List<RequiredDocument> requiredDocuments = new ArrayList<>();

    /** Tasks assigned to specific actors */
    private List<TaskAssignment> tasks = new ArrayList<>();

    /**
     * Whether the reminder for missing documents has already been sent.
     * Prevents the scheduler from sending duplicate reminders.
     */
    private boolean reminderSent = false;

    protected WorkspaceEvent() {}

    public static WorkspaceEvent create(String title, String description,
                                         EventType type, Instant startAt, Instant endAt,
                                         String location, CanonicalRef createdByRef,
                                         CanonicalRef teamRef) {
        WorkspaceEvent event = new WorkspaceEvent();
        event.initId();
        event.title = title;
        event.description = description;
        event.type = type;
        event.startAt = startAt;
        event.endAt = endAt;
        event.location = location;
        event.createdByRef = createdByRef;
        event.teamRef = teamRef;
        event.activate(); // Events are ACTIVE immediately upon creation
        return event;
    }

    public void addAttendee(AttendeeRef attendee) {
        this.attendees.add(attendee);
    }

    public void addRequiredDocument(RequiredDocument requiredDoc) {
        this.requiredDocuments.add(requiredDoc);
    }

    public void addTask(TaskAssignment task) {
        this.tasks.add(task);
    }

    public void markReminderSent() {
        this.reminderSent = true;
    }

    public void softDelete() {
        this.archive();
    }

    /**
     * True if any required document has not been submitted.
     * Used by the reminder scheduler to decide whether to send a reminder.
     */
    public boolean hasMissingDocuments() {
        return requiredDocuments.stream().anyMatch(d -> !d.isSubmitted());
    }

    // Getters
    public String getTitle()                            { return title; }
    public String getDescription()                      { return description; }
    public EventType getType()                          { return type; }
    public Instant getStartAt()                         { return startAt; }
    public Instant getEndAt()                           { return endAt; }
    public String getLocation()                         { return location; }
    public CanonicalRef getCreatedByRef()               { return createdByRef; }
    public CanonicalRef getTeamRef()                    { return teamRef; }
    public List<AttendeeRef> getAttendees()             { return List.copyOf(attendees); }
    public List<RequiredDocument> getRequiredDocuments(){ return List.copyOf(requiredDocuments); }
    public List<TaskAssignment> getTasks()              { return List.copyOf(tasks); }
    public boolean isReminderSent()                     { return reminderSent; }

    // Setters for update
    public void setTitle(String title)           { this.title = title; }
    public void setDescription(String desc)      { this.description = desc; }
    public void setStartAt(Instant startAt)      { this.startAt = startAt; }
    public void setEndAt(Instant endAt)          { this.endAt = endAt; }
    public void setLocation(String location)     { this.location = location; }
}
```

- [ ] **Step 6: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/event/domain/
git commit -m "feat(workspace/event): add EventType, AttendeeRef, RequiredDocument, TaskAssignment, WorkspaceEvent domain model"
```

---

## Task 2: Mongock Migration 002 — Event Collection Indexes

- [ ] **Step 1: Create Migration002CreateEventIndexes**

```java
// Migration002CreateEventIndexes.java
package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "migration-002-create-event-indexes", order = "002", author = "fos-team")
public class Migration002CreateEventIndexes {

    private static final String COLLECTION = "workspace_events";

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);

        // Find events by the team they belong to
        ops.ensureIndex(new Index()
                .on("teamRef.id", Sort.Direction.ASC)
                .named("idx_workspace_events_team_id"));

        // Find upcoming events (sorted by start time)
        ops.ensureIndex(new Index()
                .on("startAt", Sort.Direction.ASC)
                .named("idx_workspace_events_start_at"));

        // Find events created by a specific head coach
        ops.ensureIndex(new Index()
                .on("createdByRef.id", Sort.Direction.ASC)
                .named("idx_workspace_events_created_by"));

        // Find events by type (TRAINING, MATCH, etc.)
        ops.ensureIndex(new Index()
                .on("type", Sort.Direction.ASC)
                .named("idx_workspace_events_type"));

        // Find events by state (for reminder scheduler: only ACTIVE events)
        ops.ensureIndex(new Index()
                .on("state", Sort.Direction.ASC)
                .named("idx_workspace_events_state"));

        // Compound index: find ACTIVE events starting soon where reminder not yet sent
        // Used by EventReminderScheduler
        ops.ensureIndex(new Index()
                .on("state", Sort.Direction.ASC)
                .on("reminderSent", Sort.Direction.ASC)
                .on("startAt", Sort.Direction.ASC)
                .named("idx_workspace_events_reminder_query"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.dropIndex("idx_workspace_events_team_id");
        ops.dropIndex("idx_workspace_events_start_at");
        ops.dropIndex("idx_workspace_events_created_by");
        ops.dropIndex("idx_workspace_events_type");
        ops.dropIndex("idx_workspace_events_state");
        ops.dropIndex("idx_workspace_events_reminder_query");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration002CreateEventIndexes.java
git commit -m "feat(workspace/event): add Mongock Migration002 for event collection indexes"
```

---

## Task 3: EventRepository

- [ ] **Step 1: Create WorkspaceEventRepository**

```java
// WorkspaceEventRepository.java
package com.fos.workspace.event.infrastructure.persistence;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.event.domain.EventType;
import com.fos.workspace.event.domain.WorkspaceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceEventRepository
        extends MongoRepository<WorkspaceEvent, String> {

    Optional<WorkspaceEvent> findByResourceId(UUID resourceId);

    Page<WorkspaceEvent> findByTeamRefIdAndStateOrderByStartAtAsc(
            UUID teamRefId, ResourceState state, Pageable pageable);

    Page<WorkspaceEvent> findByTypeAndStateOrderByStartAtAsc(
            EventType type, ResourceState state, Pageable pageable);

    Page<WorkspaceEvent> findByCreatedByRefIdAndState(
            UUID createdByRefId, ResourceState state, Pageable pageable);

    /**
     * Used by the reminder scheduler.
     * Finds ACTIVE events that:
     *   - start within the next 24 hours
     *   - have not had a reminder sent yet
     */
    @Query("{ 'state': 'ACTIVE', 'reminderSent': false, " +
           "'startAt': { $gte: ?0, $lte: ?1 } }")
    List<WorkspaceEvent> findUpcomingEventsNeedingReminder(
            Instant from, Instant to);
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/event/infrastructure/
git commit -m "feat(workspace/event): add WorkspaceEventRepository"
```

---

## Task 4: Reminder Strategy Pattern

**Why:** The reminder logic is extracted into a `ReminderStrategy` interface. This follows the Strategy pattern — if we want to add a different reminder rule later (e.g., task deadline reminder), we add a new class that implements the same interface without touching the scheduler.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/application/reminder/ReminderStrategy.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/application/reminder/DocumentMissingReminderStrategy.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/event/application/reminder/EventReminderScheduler.java`

- [ ] **Step 1: Create ReminderStrategy interface**

```java
// ReminderStrategy.java
package com.fos.workspace.event.application.reminder;

import com.fos.workspace.event.domain.WorkspaceEvent;

/**
 * Strategy interface for reminder logic.
 * Each implementation decides whether an event needs a reminder
 * and emits the appropriate signal.
 */
public interface ReminderStrategy {
    /**
     * Returns true if this event should trigger a reminder right now.
     */
    boolean shouldRemind(WorkspaceEvent event);

    /**
     * Sends the reminder (emits Kafka signal).
     * Called only if shouldRemind() returns true.
     */
    void sendReminder(WorkspaceEvent event);
}
```

- [ ] **Step 2: Create DocumentMissingReminderStrategy**

```java
// DocumentMissingReminderStrategy.java
package com.fos.workspace.event.application.reminder;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.workspace.event.domain.WorkspaceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sends an ALERT signal when an event has required documents that
 * have not been submitted yet.
 *
 * The ALERT signal is consumed by the notification package (Sprint 1.4)
 * which delivers an in-app notification to the responsible actors.
 */
@Component
public class DocumentMissingReminderStrategy implements ReminderStrategy {

    private static final Logger log = LoggerFactory.getLogger(DocumentMissingReminderStrategy.class);

    private final FosKafkaProducer kafkaProducer;

    public DocumentMissingReminderStrategy(FosKafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public boolean shouldRemind(WorkspaceEvent event) {
        // Only send reminder if there are outstanding required documents
        return event.hasMissingDocuments();
    }

    @Override
    public void sendReminder(WorkspaceEvent event) {
        // Emit an ALERT signal for each actor with a missing document
        event.getRequiredDocuments().stream()
                .filter(req -> !req.isSubmitted())
                .forEach(req -> {
                    kafkaProducer.emit(SignalEnvelope.builder()
                            .type(SignalType.ALERT)
                            .topic("fos.workspace.event.document.missing")
                            .actorRef(CanonicalRef.of(CanonicalType.CLUB,
                                    req.getAssignedToActorId()).toString())
                            .build());

                    log.info("Sent missing-document reminder: eventId={} actor={} requirement='{}'",
                            event.getResourceId(),
                            req.getAssignedToActorId(),
                            req.getDescription());
                });
    }
}
```

- [ ] **Step 3: Create EventReminderScheduler**

```java
// EventReminderScheduler.java
package com.fos.workspace.event.application.reminder;

import com.fos.workspace.event.domain.WorkspaceEvent;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Runs every hour and looks for upcoming events that need reminders.
 *
 * Logic:
 *   - Query MongoDB for ACTIVE events starting in the next 25 hours
 *     where reminderSent = false
 *   - For each: run DocumentMissingReminderStrategy
 *   - If reminder was sent: set reminderSent = true so it doesn't fire again
 *
 * WHY @Scheduled instead of a real job scheduler?
 * Phase 1 has one instance. @Scheduled is simple, requires no extra infra,
 * and is easy to test. When we go multi-instance, we add a distributed lock
 * (via ShedLock or Kafka Streams) around this method.
 */
@Component
public class EventReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventReminderScheduler.class);

    private final WorkspaceEventRepository eventRepository;
    private final ReminderStrategy reminderStrategy;

    public EventReminderScheduler(WorkspaceEventRepository eventRepository,
                                   ReminderStrategy reminderStrategy) {
        this.eventRepository = eventRepository;
        this.reminderStrategy = reminderStrategy;
    }

    /**
     * Runs every hour at the top of the hour.
     * "0 0 * * * *" = at second 0, minute 0, every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void sendEventReminders() {
        Instant now = Instant.now();
        Instant in25Hours = now.plus(25, ChronoUnit.HOURS);

        List<WorkspaceEvent> upcomingEvents =
                eventRepository.findUpcomingEventsNeedingReminder(now, in25Hours);

        log.info("Reminder check: {} upcoming events found", upcomingEvents.size());

        for (WorkspaceEvent event : upcomingEvents) {
            if (reminderStrategy.shouldRemind(event)) {
                reminderStrategy.sendReminder(event);
                event.markReminderSent();
                eventRepository.save(event);
            }
        }
    }
}
```

- [ ] **Step 4: Enable scheduling in WorkspaceApp**

Add `@EnableScheduling` to `WorkspaceApp.java`:

```java
@SpringBootApplication
@EnableMongock
@org.springframework.scheduling.annotation.EnableScheduling
public class WorkspaceApp {
    public static void main(String[] args) {
        SpringApplication.run(WorkspaceApp.class, args);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/event/application/
git commit -m "feat(workspace/event): add ReminderStrategy, DocumentMissingReminderStrategy, EventReminderScheduler"
```

---

## Task 5: EventService + API DTOs + EventController

- [ ] **Step 1: Create API DTOs**

```java
// CreateEventRequest.java
package com.fos.workspace.event.api;

import com.fos.workspace.event.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
    @NotBlank String title,
    String description,
    @NotNull EventType type,
    @NotNull Instant startAt,
    @NotNull Instant endAt,
    String location,
    UUID teamRefId,
    List<AttendeeInput> attendees,
    List<RequiredDocumentInput> requiredDocuments,
    List<TaskInput> tasks
) {
    public record AttendeeInput(UUID actorId, boolean mandatory,
                                 String canonicalType) {} // "PLAYER" or "CLUB"
    public record RequiredDocumentInput(String description,
                                         String documentCategory,
                                         UUID assignedToActorId) {}
    public record TaskInput(String title, String description,
                             UUID assignedToActorId, Instant dueAt) {}
}
```

```java
// UpdateEventRequest.java
package com.fos.workspace.event.api;

import java.time.Instant;

public record UpdateEventRequest(
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    String location
) {}
```

```java
// EventResponse.java
package com.fos.workspace.event.api;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.event.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
    UUID eventId,
    String title,
    String description,
    EventType type,
    Instant startAt,
    Instant endAt,
    String location,
    UUID createdByActorId,
    UUID teamRefId,
    ResourceState state,
    List<AttendeeRef> attendees,
    List<RequiredDocument> requiredDocuments,
    List<TaskAssignment> tasks,
    boolean reminderSent,
    Instant createdAt
) {
    public static EventResponse from(WorkspaceEvent e) {
        return new EventResponse(
                e.getResourceId(), e.getTitle(), e.getDescription(), e.getType(),
                e.getStartAt(), e.getEndAt(), e.getLocation(),
                e.getCreatedByRef() != null ? e.getCreatedByRef().id() : null,
                e.getTeamRef() != null ? e.getTeamRef().id() : null,
                e.getState(), e.getAttendees(), e.getRequiredDocuments(),
                e.getTasks(), e.isReminderSent(), e.getCreatedAt());
    }
}
```

- [ ] **Step 2: Create EventService**

```java
// EventService.java
package com.fos.workspace.event.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.workspace.event.api.CreateEventRequest;
import com.fos.workspace.event.api.EventResponse;
import com.fos.workspace.event.api.UpdateEventRequest;
import com.fos.workspace.event.domain.*;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EventService {

    private final WorkspaceEventRepository eventRepository;
    private final PolicyClient policyClient;
    private final FosKafkaProducer kafkaProducer;

    public EventService(WorkspaceEventRepository eventRepository,
                        PolicyClient policyClient,
                        FosKafkaProducer kafkaProducer) {
        this.eventRepository = eventRepository;
        this.policyClient = policyClient;
        this.kafkaProducer = kafkaProducer;
    }

    public EventResponse createEvent(CreateEventRequest request) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        // Permission check
        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, "workspace.event.create",
                CanonicalRef.of(CanonicalType.CLUB, actorId), "ACTIVE"));
        if (!policy.isAllowed()) throw new AccessDeniedException(policy.reason());

        CanonicalRef createdByRef = CanonicalRef.of(CanonicalType.CLUB, actorId);
        CanonicalRef teamRef = request.teamRefId() != null
                ? CanonicalRef.of(CanonicalType.TEAM, request.teamRefId()) : null;

        WorkspaceEvent event = WorkspaceEvent.create(
                request.title(), request.description(), request.type(),
                request.startAt(), request.endAt(), request.location(),
                createdByRef, teamRef);

        // Add attendees
        if (request.attendees() != null) {
            for (var a : request.attendees()) {
                CanonicalType type = "PLAYER".equals(a.canonicalType())
                        ? CanonicalType.PLAYER : CanonicalType.CLUB;
                event.addAttendee(new AttendeeRef(
                        CanonicalRef.of(type, a.actorId()), a.mandatory()));
            }
        }

        // Add required documents
        if (request.requiredDocuments() != null) {
            for (var rd : request.requiredDocuments()) {
                event.addRequiredDocument(new RequiredDocument(
                        rd.description(), rd.documentCategory(), rd.assignedToActorId()));
            }
        }

        // Add tasks
        if (request.tasks() != null) {
            for (var t : request.tasks()) {
                event.addTask(new TaskAssignment(
                        t.title(), t.description(), t.assignedToActorId(), t.dueAt()));
            }
        }

        WorkspaceEvent saved = eventRepository.save(event);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("fos.workspace.event.created")
                .actorRef(createdByRef.toString())
                .build());

        return EventResponse.from(saved);
    }

    public EventResponse getEvent(UUID eventId) {
        WorkspaceEvent event = loadEvent(eventId);
        return EventResponse.from(event);
    }

    public Page<EventResponse> listEventsByTeam(UUID teamRefId, Pageable pageable) {
        return eventRepository
                .findByTeamRefIdAndStateOrderByStartAtAsc(
                        teamRefId, com.fos.sdk.core.ResourceState.ACTIVE, pageable)
                .map(EventResponse::from);
    }

    public EventResponse updateEvent(UUID eventId, UpdateEventRequest request) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        WorkspaceEvent event = loadEvent(eventId);

        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, "workspace.event.update",
                CanonicalRef.of(CanonicalType.CLUB, actorId),
                event.getState().name()));
        if (!policy.isAllowed()) throw new AccessDeniedException(policy.reason());

        if (request.title() != null) event.setTitle(request.title());
        if (request.description() != null) event.setDescription(request.description());
        if (request.startAt() != null) event.setStartAt(request.startAt());
        if (request.endAt() != null) event.setEndAt(request.endAt());
        if (request.location() != null) event.setLocation(request.location());

        WorkspaceEvent saved = eventRepository.save(event);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("fos.workspace.event.updated")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .build());

        return EventResponse.from(saved);
    }

    public void deleteEvent(UUID eventId) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        WorkspaceEvent event = loadEvent(eventId);

        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, "workspace.event.delete",
                CanonicalRef.of(CanonicalType.CLUB, actorId),
                event.getState().name()));
        if (!policy.isAllowed()) throw new AccessDeniedException(policy.reason());

        event.softDelete();
        eventRepository.save(event);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic("fos.audit.all")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .build());
    }

    private WorkspaceEvent loadEvent(UUID eventId) {
        return eventRepository.findByResourceId(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
    }
}
```

- [ ] **Step 3: Create EventController**

```java
// EventController.java
package com.fos.workspace.event.api;

import com.fos.workspace.event.application.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable UUID eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping
    public Page<EventResponse> listEventsByTeam(
            @RequestParam UUID teamRefId,
            @PageableDefault(size = 20) Pageable pageable) {
        return eventService.listEventsByTeam(teamRefId, pageable);
    }

    @PutMapping("/{eventId}")
    public EventResponse updateEvent(@PathVariable UUID eventId,
                                      @RequestBody UpdateEventRequest request) {
        return eventService.updateEvent(eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID eventId) {
        eventService.deleteEvent(eventId);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/event/
git commit -m "feat(workspace/event): add EventService, EventController, CreateEventRequest, UpdateEventRequest, EventResponse"
```

---

## Task 6: OPA Policy — Workspace Event Rules

- [ ] **Step 1: Add event rules to workspace_policy.rego**

Append to `fos-governance-service/src/main/resources/opa/workspace_policy.rego`:

```rego
# ── Workspace Event Policies ─────────────────────────────────────────────────

# HEAD_COACH and ADMIN can create, update, delete events
allow {
    input.resource.action == "workspace.event.create"
    event_manage_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.event.update"
    event_manage_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.event.delete"
    event_manage_roles[input.actor.role]
}

# All coaching staff can read events
allow {
    input.resource.action == "workspace.event.read"
    coaching_staff_roles[input.actor.role]
}

event_manage_roles := {
    "ROLE_HEAD_COACH",
    "ROLE_CLUB_ADMIN"
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-governance-service/src/main/resources/opa/workspace_policy.rego
git commit -m "feat(governance/opa): add workspace event policy rules"
```

---

## Task 7: Integration Tests

- [ ] **Step 1: Create EventIntegrationTest**

```java
// EventIntegrationTest.java
package com.fos.workspace.event;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.event.api.CreateEventRequest;
import com.fos.workspace.event.api.EventResponse;
import com.fos.workspace.event.api.UpdateEventRequest;
import com.fos.workspace.event.domain.EventType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",
    "spring.security.enabled=false"
})
class EventIntegrationTest extends FosTestContainersBase {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() { wireMock.stop(); }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("fos.policy.service-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void stubPolicyAllow() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_create_event_and_return_201() {
        var request = new CreateEventRequest(
                "Pre-Season Training", "First training of the season",
                EventType.TRAINING,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                "Training Ground A",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of());

        ResponseEntity<EventResponse> response = restTemplate.postForEntity(
                "/api/v1/events", request, EventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().title()).isEqualTo("Pre-Season Training");
        assertThat(response.getBody().type()).isEqualTo(EventType.TRAINING);
        assertThat(response.getBody().state().name()).isEqualTo("ACTIVE");
    }

    @Test
    void should_update_event_title() {
        var createRequest = new CreateEventRequest(
                "Original Title", null, EventType.MEETING,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null, UUID.randomUUID(), List.of(), List.of(), List.of());

        EventResponse created = restTemplate.postForObject(
                "/api/v1/events", createRequest, EventResponse.class);

        var updateRequest = new UpdateEventRequest("Updated Title", null, null, null, null);

        restTemplate.put("/api/v1/events/" + created.eventId(), updateRequest);

        EventResponse updated = restTemplate.getForObject(
                "/api/v1/events/" + created.eventId(), EventResponse.class);

        assertThat(updated.title()).isEqualTo("Updated Title");
    }

    @Test
    void should_delete_event_and_return_204() {
        var createRequest = new CreateEventRequest(
                "To Delete", null, EventType.OTHER,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null, UUID.randomUUID(), List.of(), List.of(), List.of());

        EventResponse created = restTemplate.postForObject(
                "/api/v1/events", createRequest, EventResponse.class);

        restTemplate.delete("/api/v1/events/" + created.eventId());

        EventResponse deleted = restTemplate.getForObject(
                "/api/v1/events/" + created.eventId(), EventResponse.class);

        assertThat(deleted.state().name()).isEqualTo("ARCHIVED");
    }

    @Test
    void should_list_events_by_team() {
        UUID teamId = UUID.randomUUID();

        // Create 2 events for this team
        for (int i = 1; i <= 2; i++) {
            var req = new CreateEventRequest("Event " + i, null, EventType.TRAINING,
                    Instant.now().plus(i, ChronoUnit.DAYS),
                    Instant.now().plus(i, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                    null, teamId, List.of(), List.of(), List.of());
            restTemplate.postForObject("/api/v1/events", req, EventResponse.class);
        }

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/events?teamRefId=" + teamId, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

- [ ] **Step 2: Run tests**

```bash
cd fos-workspace-service
mvn test -Dtest=EventIntegrationTest -q
```

Expected: BUILD SUCCESS — 4 tests pass

- [ ] **Step 3: Commit**

```bash
git add fos-workspace-service/src/test/java/com/fos/workspace/event/
git commit -m "test(workspace/event): add EventIntegrationTest — CRUD and list"
```

---

## Task 8: Full Build Verification

- [ ] **Step 1: Run all workspace tests**

```bash
cd fos-workspace-service
mvn test -q
```

Expected: All tests pass — DocumentIntegrationTest (3) + EventIntegrationTest (4) = 7 tests

- [ ] **Step 2: Full monorepo build**

```bash
cd football-os-core
mvn package -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit**

```bash
git commit -m "chore(workspace): sprint 1.2 complete — calendar event management, reminder strategy, OPA policies"
```

---

## Sprint Test Criteria

Sprint 1.2 is complete when:

1. All 7 tests pass (3 document + 4 event)
2. `POST /api/v1/events` with HEAD_COACH role → 201 Created
3. `PUT /api/v1/events/{id}` → 200 with updated fields
4. `DELETE /api/v1/events/{id}` → 204 and state=ARCHIVED
5. `GET /api/v1/events?teamRefId=...` → 200 with paginated list
6. Mongock Migration002 ran (verifiable in `mongockChangeLog`)
7. `EventReminderScheduler` is wired and runs on its cron schedule
8. ALERT signals emitted for missing documents (verified via `SignalCaptor` or log output)

---

## What NOT to Include in This Sprint

- **Player profile linking to events** — Sprint 1.3
- **Frontend calendar UI** — Sprint 1.5
- **Push notifications** — Sprint 1.4
- **Recurring events** — Phase 2+
- **iCal export** — Phase 2+
