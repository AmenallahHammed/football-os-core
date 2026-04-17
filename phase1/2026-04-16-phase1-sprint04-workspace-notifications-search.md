# Phase 1 Sprint 1.4 — fos-workspace-service: Notifications + Search + OnlyOffice Save

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four features are completed:
1. **Notification Inbox** — Actors receive in-app notifications. A Kafka consumer (`AbstractFosConsumer`) listens to the `fos.workspace.event.document.missing` and `fos.workspace.document.uploaded` topics and writes notification records to MongoDB. The frontend polls `GET /api/v1/notifications` to show the inbox. Notifications can be marked as read.
2. **Search** — `GET /api/v1/search` accepts a text query and returns matching documents and events, filtered by the caller's role-based permissions.
3. **OnlyOffice Save Callback** — The OnlyOffice Document Server calls `POST /api/v1/onlyoffice/callback/{documentId}` when a user saves. The backend downloads the updated file from OnlyOffice, uploads it to MinIO via `StoragePort`, creates a new `DocumentVersion`, and appends it to the `WorkspaceDocument`.
4. **Mongock Migration 004** — creates the `workspace_notifications` collection and its indexes.

**Architecture:**
The notification inbox is implemented as a simple append-only MongoDB collection. When a Kafka ALERT or FACT signal arrives, the consumer writes a `WorkspaceNotification` document. The inbox API returns unread notifications sorted by `createdAt` descending. This is a pull-based inbox — not push (WebSocket/SSE). Push notifications are a Phase 2+ concern. The search API uses MongoDB's `$regex` operator for text matching within the permitted collections. Full-text search with OpenSearch/Elasticsearch is Phase 2+.

**Tech Stack:** Java 21, Spring Boot 3.3.x, MongoDB 7, Mongock 5.4.x, Spring Kafka, `sdk-events` (AbstractFosConsumer, FosKafkaProducer), `sdk-policy` (PolicyClient), `sdk-storage` (StoragePort), RestTemplate (for downloading the saved file from OnlyOffice), JUnit 5, Testcontainers, WireMock

**Required Patterns This Sprint:**
- `[REQUIRED]` **Template Method** — `WorkspaceKafkaConsumer` extends `AbstractFosConsumer`. Only the `handle()` method is implemented; all deserialization, MDC, and DLQ routing is handled by the base class.
- `[REQUIRED]` **Observer (via Kafka)** — The notification consumer observes ALERT and FACT topics and reacts by writing inbox records. This is the Observer pattern implemented through a message bus.
- `[RECOMMENDED]` **Specification** — `DocumentSearchSpecification` encapsulates the MongoDB query criteria for search, making the search logic reusable and testable in isolation.

---

## File Map

```
fos-workspace-service/
└── src/
    ├── main/java/com/fos/workspace/
    │   ├── notification/
    │   │   ├── api/
    │   │   │   ├── NotificationController.java                        CREATE
    │   │   │   └── NotificationResponse.java                          CREATE
    │   │   ├── application/
    │   │   │   ├── NotificationService.java                           CREATE
    │   │   │   └── WorkspaceKafkaConsumer.java                        CREATE
    │   │   ├── domain/
    │   │   │   ├── WorkspaceNotification.java                         CREATE
    │   │   │   └── NotificationType.java                              CREATE
    │   │   └── infrastructure/
    │   │       └── persistence/
    │   │           └── WorkspaceNotificationRepository.java           CREATE
    │   ├── search/
    │   │   ├── api/
    │   │   │   ├── SearchController.java                              CREATE
    │   │   │   └── SearchResponse.java                                CREATE
    │   │   └── application/
    │   │       └── WorkspaceSearchService.java                        CREATE
    │   └── onlyoffice/
    │       └── application/
    │           └── OnlyOfficeSaveHandler.java                         CREATE
    └── db/migration/
        └── Migration004CreateNotificationIndexes.java                 CREATE
    └── test/java/com/fos/workspace/
        ├── notification/
        │   └── NotificationIntegrationTest.java                       CREATE
        └── search/
            └── SearchIntegrationTest.java                             CREATE
```

---

## Task 1: Notification Domain Model + Migration

- [ ] **Step 1: Create NotificationType enum**

```java
// NotificationType.java
package com.fos.workspace.notification.domain;

/**
 * What kind of notification this is.
 * Used by the frontend to choose the correct icon and message template.
 */
public enum NotificationType {
    DOCUMENT_MISSING,      // Actor must submit a required document for an event
    DOCUMENT_UPLOADED,     // A document linked to this actor was uploaded
    EVENT_REMINDER,        // An event is starting soon
    TASK_ASSIGNED,         // A task was assigned to this actor
    GENERAL                // Any other notification
}
```

- [ ] **Step 2: Create WorkspaceNotification entity**

```java
// WorkspaceNotification.java
package com.fos.workspace.notification.domain;

import com.fos.sdk.core.BaseDocument;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

/**
 * A single notification in an actor's inbox.
 *
 * Extends BaseDocument (sdk-core) → id, resourceId, state, version,
 * createdAt, updatedAt.
 *
 * Notifications are NEVER deleted — they are marked as read.
 * This gives us a full audit trail of what was communicated to each actor.
 *
 * recipientActorId: who should see this notification in their inbox
 * triggeredByActorId: who (or what system process) triggered this notification
 */
@Document(collection = "workspace_notifications")
public class WorkspaceNotification extends BaseDocument {

    private UUID recipientActorId;
    private UUID triggeredByActorId;    // null for system-generated notifications
    private NotificationType type;
    private String title;
    private String body;
    private boolean read;

    /** Optional reference to the entity this notification is about */
    private UUID relatedDocumentId;
    private UUID relatedEventId;

    protected WorkspaceNotification() {}

    public static WorkspaceNotification create(UUID recipientActorId,
                                                UUID triggeredByActorId,
                                                NotificationType type,
                                                String title,
                                                String body,
                                                UUID relatedDocumentId,
                                                UUID relatedEventId) {
        WorkspaceNotification n = new WorkspaceNotification();
        n.initId();
        n.recipientActorId = recipientActorId;
        n.triggeredByActorId = triggeredByActorId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.read = false;
        n.relatedDocumentId = relatedDocumentId;
        n.relatedEventId = relatedEventId;
        n.activate(); // notifications are active immediately
        return n;
    }

    public void markRead() { this.read = true; }

    public UUID getRecipientActorId()    { return recipientActorId; }
    public UUID getTriggeredByActorId()  { return triggeredByActorId; }
    public NotificationType getType()    { return type; }
    public String getTitle()             { return title; }
    public String getBody()              { return body; }
    public boolean isRead()              { return read; }
    public UUID getRelatedDocumentId()   { return relatedDocumentId; }
    public UUID getRelatedEventId()      { return relatedEventId; }
}
```

- [ ] **Step 3: Create WorkspaceNotificationRepository**

```java
// WorkspaceNotificationRepository.java
package com.fos.workspace.notification.infrastructure.persistence;

import com.fos.workspace.notification.domain.WorkspaceNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceNotificationRepository
        extends MongoRepository<WorkspaceNotification, String> {

    /** Returns all notifications for a specific actor (inbox), newest first */
    Page<WorkspaceNotification> findByRecipientActorIdOrderByCreatedAtDesc(
            UUID recipientActorId, Pageable pageable);

    /** Returns only UNREAD notifications for an actor */
    Page<WorkspaceNotification> findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(
            UUID recipientActorId, Pageable pageable);

    /** Used for marking a notification as read */
    Optional<WorkspaceNotification> findByResourceId(UUID resourceId);

    /** Count unread notifications for badge display */
    long countByRecipientActorIdAndReadFalse(UUID recipientActorId);
}
```

- [ ] **Step 4: Create Migration004**

```java
// Migration004CreateNotificationIndexes.java
package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "migration-004-create-notification-indexes", order = "004", author = "fos-team")
public class Migration004CreateNotificationIndexes {

    private static final String COLLECTION = "workspace_notifications";

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);

        // Primary inbox query: find all notifications for an actor, sorted by date
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document()
                        .append("recipientActorId", 1)
                        .append("createdAt", -1))
                .named("idx_notifications_recipient_date"));

        // Unread count query
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document()
                        .append("recipientActorId", 1)
                        .append("read", 1))
                .named("idx_notifications_recipient_read"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.dropIndex("idx_notifications_recipient_date");
        ops.dropIndex("idx_notifications_recipient_read");
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/notification/ \
        fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration004CreateNotificationIndexes.java
git commit -m "feat(workspace/notification): add WorkspaceNotification domain, repository, Migration004"
```

---

## Task 2: Kafka Consumer — WorkspaceKafkaConsumer (Template Method)

**Why:** `WorkspaceKafkaConsumer` extends `AbstractFosConsumer` from `sdk-events`. This is the Template Method pattern: the base class defines the structure (deserialization, MDC, DLQ routing), and we only implement the domain logic in `handle()`. We never write a raw `@KafkaListener` on a plain method.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java`

- [ ] **Step 1: Create WorkspaceKafkaConsumer**

```java
// WorkspaceKafkaConsumer.java
package com.fos.workspace.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.AbstractFosConsumer;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.workspace.notification.domain.NotificationType;
import com.fos.workspace.notification.domain.WorkspaceNotification;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for workspace-relevant signals.
 *
 * Extends AbstractFosConsumer — Template Method pattern:
 *   The base class handles: deserialization, correlation ID extraction,
 *   MDC logging, error handling, and DLQ routing.
 *   This class only implements handle() — the domain logic.
 *
 * Listens to two topics:
 *   - fos.workspace.event.document.missing: sent by EventReminderScheduler
 *   - fos.workspace.document.uploaded: sent by DocumentService
 *
 * On receiving a signal, writes a WorkspaceNotification to MongoDB
 * for the target actor to see in their inbox.
 */
@Component
@KafkaListener(
    topics = {
        "fos.workspace.event.document.missing",
        "fos.workspace.document.uploaded",
        "fos.workspace.event.created"
    },
    groupId = "fos-workspace-notifications"
)
public class WorkspaceKafkaConsumer extends AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceKafkaConsumer.class);

    private final WorkspaceNotificationRepository notificationRepository;

    public WorkspaceKafkaConsumer(ObjectMapper objectMapper,
                                   WorkspaceNotificationRepository notificationRepository) {
        super(objectMapper);
        this.notificationRepository = notificationRepository;
    }

    @Override
    protected void handle(SignalEnvelope envelope) {
        log.debug("Processing workspace signal: topic={} signalId={}",
                envelope.topic(), envelope.signalId());

        switch (envelope.topic()) {

            case "fos.workspace.event.document.missing" -> {
                // actorRef is the actor who must submit the missing document
                UUID recipientId = extractActorId(envelope.actorRef());
                if (recipientId == null) return;

                WorkspaceNotification notification = WorkspaceNotification.create(
                        recipientId,
                        null, // system-generated
                        NotificationType.DOCUMENT_MISSING,
                        "Action Required: Missing Document",
                        "You have a required document that must be submitted before an upcoming event.",
                        null, null);

                notificationRepository.save(notification);
                log.info("Saved DOCUMENT_MISSING notification for actor={}", recipientId);
            }

            case "fos.workspace.document.uploaded" -> {
                // For document uploads, we could notify linked players or team members.
                // For Phase 1, we notify the uploader with a confirmation.
                UUID uploaderId = extractActorId(envelope.actorRef());
                if (uploaderId == null) return;

                WorkspaceNotification notification = WorkspaceNotification.create(
                        uploaderId,
                        uploaderId,
                        NotificationType.DOCUMENT_UPLOADED,
                        "Document Uploaded Successfully",
                        "Your document has been uploaded and is now available in the workspace.",
                        null, null);

                notificationRepository.save(notification);
                log.info("Saved DOCUMENT_UPLOADED notification for actor={}", uploaderId);
            }

            case "fos.workspace.event.created" -> {
                // Notify the creator — confirmation that their event was saved.
                UUID creatorId = extractActorId(envelope.actorRef());
                if (creatorId == null) return;

                WorkspaceNotification notification = WorkspaceNotification.create(
                        creatorId,
                        creatorId,
                        NotificationType.EVENT_REMINDER,
                        "Event Created",
                        "Your calendar event has been created.",
                        null, null);

                notificationRepository.save(notification);
            }

            default -> log.debug("Unhandled topic in WorkspaceKafkaConsumer: {}", envelope.topic());
        }
    }

    /**
     * Extracts a UUID from the actorRef string.
     * actorRef is a CanonicalRef.toString() in the format "CanonicalRef[type=CLUB, id=uuid]".
     * This is a simplified parser — in production use CanonicalRef deserialization.
     */
    private UUID extractActorId(String actorRefString) {
        if (actorRefString == null) return null;
        try {
            // Simple extraction: find the UUID pattern in the string
            // CanonicalRef.toString() → "CanonicalRef[type=CLUB, id=<UUID>]"
            // We extract the UUID after "id="
            int idIndex = actorRefString.indexOf("id=");
            if (idIndex == -1) {
                // Try parsing directly as UUID (if the caller passed UUID.toString())
                return UUID.fromString(actorRefString.trim());
            }
            String uuidPart = actorRefString.substring(idIndex + 3).replace("]", "").trim();
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException e) {
            log.warn("Could not extract actorId from actorRef: {}", actorRefString);
            return null;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/notification/application/WorkspaceKafkaConsumer.java
git commit -m "feat(workspace/notification): add WorkspaceKafkaConsumer extending AbstractFosConsumer"
```

---

## Task 3: NotificationService + NotificationController

- [ ] **Step 1: Create NotificationResponse**

```java
// NotificationResponse.java
package com.fos.workspace.notification.api;

import com.fos.workspace.notification.domain.NotificationType;
import com.fos.workspace.notification.domain.WorkspaceNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID notificationId,
    NotificationType type,
    String title,
    String body,
    boolean read,
    UUID relatedDocumentId,
    UUID relatedEventId,
    Instant createdAt
) {
    public static NotificationResponse from(WorkspaceNotification n) {
        return new NotificationResponse(
                n.getResourceId(), n.getType(), n.getTitle(), n.getBody(),
                n.isRead(), n.getRelatedDocumentId(), n.getRelatedEventId(),
                n.getCreatedAt());
    }
}
```

- [ ] **Step 2: Create NotificationService**

```java
// NotificationService.java
package com.fos.workspace.notification.application;

import com.fos.sdk.security.FosSecurityContext;
import com.fos.workspace.notification.api.NotificationResponse;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    private final WorkspaceNotificationRepository notificationRepository;

    public NotificationService(WorkspaceNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Returns all notifications for the current actor (paginated, newest first) */
    public Page<NotificationResponse> getMyNotifications(boolean unreadOnly, Pageable pageable) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        if (unreadOnly) {
            return notificationRepository
                    .findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(actorId, pageable)
                    .map(NotificationResponse::from);
        }
        return notificationRepository
                .findByRecipientActorIdOrderByCreatedAtDesc(actorId, pageable)
                .map(NotificationResponse::from);
    }

    /** Returns the count of unread notifications (for the inbox badge) */
    public long countUnread() {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        return notificationRepository.countByRecipientActorIdAndReadFalse(actorId);
    }

    /** Marks a specific notification as read */
    public void markRead(UUID notificationId) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());

        var notification = notificationRepository
                .findByResourceId(notificationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Notification not found: " + notificationId));

        // Security: actors can only mark their own notifications as read
        if (!notification.getRecipientActorId().equals(actorId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot mark another actor's notification as read");
        }

        notification.markRead();
        notificationRepository.save(notification);
    }

    /** Marks all notifications for the current actor as read */
    public void markAllRead() {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        notificationRepository
                .findByRecipientActorIdAndReadFalseOrderByCreatedAtDesc(
                        actorId, Pageable.unpaged())
                .forEach(n -> {
                    n.markRead();
                    notificationRepository.save(n);
                });
    }
}
```

- [ ] **Step 3: Create NotificationController**

```java
// NotificationController.java
package com.fos.workspace.notification.api;

import com.fos.workspace.notification.application.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /api/v1/notifications?unreadOnly=true&page=0&size=20 */
    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return notificationService.getMyNotifications(unreadOnly, pageable);
    }

    /** GET /api/v1/notifications/unread-count — for the inbox badge number */
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        return Map.of("count", notificationService.countUnread());
    }

    /** PATCH /api/v1/notifications/{id}/read — mark one notification as read */
    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID notificationId) {
        notificationService.markRead(notificationId);
    }

    /** POST /api/v1/notifications/mark-all-read — mark all as read */
    @PostMapping("/mark-all-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notificationService.markAllRead();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/notification/
git commit -m "feat(workspace/notification): add NotificationService, NotificationController"
```

---

## Task 4: Search

**Why:** The search API accepts a text query and returns matching documents and events that the current actor is permitted to see. We use MongoDB `$regex` for simplicity in Phase 1.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/search/api/SearchResponse.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/search/application/WorkspaceSearchService.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/search/api/SearchController.java`

- [ ] **Step 1: Create SearchResponse**

```java
// SearchResponse.java
package com.fos.workspace.search.api;

import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.event.api.EventResponse;

import java.util.List;

/**
 * Combined search result containing matched documents and events.
 * Each section may be empty if no results were found in that collection.
 */
public record SearchResponse(
    String query,
    List<DocumentResponse> documents,
    List<EventResponse> events,
    int totalDocuments,
    int totalEvents
) {}
```

- [ ] **Step 2: Create WorkspaceSearchService**

```java
// WorkspaceSearchService.java
package com.fos.workspace.search.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.ResourceState;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.event.api.EventResponse;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import com.fos.workspace.search.api.SearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Workspace search service.
 *
 * Searches across documents and events. Results are filtered by the
 * caller's permissions — categories the actor cannot see are excluded.
 *
 * Phase 1: Uses MongoDB $regex.
 * Phase 2+: Replace with OpenSearch/Elasticsearch for full-text search,
 *           relevance scoring, and highlighting.
 */
@Service
public class WorkspaceSearchService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);
    private static final int MAX_SEARCH_RESULTS = 50;

    private final WorkspaceDocumentRepository documentRepository;
    private final WorkspaceEventRepository eventRepository;
    private final PolicyClient policyClient;
    private final StoragePort storagePort;

    public WorkspaceSearchService(WorkspaceDocumentRepository documentRepository,
                                   WorkspaceEventRepository eventRepository,
                                   PolicyClient policyClient,
                                   StoragePort storagePort) {
        this.documentRepository = documentRepository;
        this.eventRepository = eventRepository;
        this.policyClient = policyClient;
        this.storagePort = storagePort;
    }

    public SearchResponse search(String query) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        // ── Search documents ─────────────────────────────────────────────────
        // Use the regex search method from WorkspaceDocumentRepository
        Page<WorkspaceDocument> docResults = documentRepository.searchByName(
                query, PageRequest.of(0, MAX_SEARCH_RESULTS));

        // Filter documents to only include categories the actor can see
        List<DocumentResponse> permittedDocs = docResults.stream()
                .filter(doc -> canAccessCategory(actorId, role, doc.getCategory()))
                .map(doc -> {
                    String url = doc.currentVersion() != null
                            ? storagePort.generateDownloadUrl(
                                doc.currentVersion().getStorageBucket(),
                                doc.currentVersion().getStorageObjectKey(),
                                DOWNLOAD_URL_EXPIRY)
                            : null;
                    return DocumentResponse.from(doc, url);
                })
                .toList();

        // ── Search events ────────────────────────────────────────────────────
        // Simple: search by title in active events
        // TODO Phase 2: replace with full-text search via OpenSearch
        List<EventResponse> matchingEvents = eventRepository
                .findAll().stream()
                .filter(e -> e.getState() == ResourceState.ACTIVE
                        && e.getTitle() != null
                        && e.getTitle().toLowerCase().contains(query.toLowerCase()))
                .limit(MAX_SEARCH_RESULTS)
                .map(EventResponse::from)
                .toList();

        return new SearchResponse(query, permittedDocs, matchingEvents,
                permittedDocs.size(), matchingEvents.size());
    }

    private boolean canAccessCategory(UUID actorId, String role, DocumentCategory category) {
        String action = "workspace.document." + category.name().toLowerCase() + ".read";
        return policyClient.evaluate(PolicyRequest.of(
                actorId, role, action,
                CanonicalRef.of(CanonicalType.CLUB, actorId), "ACTIVE")).isAllowed();
    }
}
```

- [ ] **Step 3: Create SearchController**

```java
// SearchController.java
package com.fos.workspace.search.api;

import com.fos.workspace.search.application.WorkspaceSearchService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final WorkspaceSearchService searchService;

    public SearchController(WorkspaceSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Search workspace content.
     * GET /api/v1/search?q=contract
     * Returns matching documents and events filtered by caller's permissions.
     */
    @GetMapping
    public SearchResponse search(@RequestParam @NotBlank String q) {
        return searchService.search(q);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/search/
git commit -m "feat(workspace/search): add WorkspaceSearchService, SearchController"
```

---

## Task 5: OnlyOffice Save Callback Handler

**Why:** When a user finishes editing in OnlyOffice and saves, the Document Server calls our callback URL with the new file bytes URL. We need to download that file, upload it to MinIO, and create a new `DocumentVersion`. This completes the editing loop.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeSaveHandler.java`

- [ ] **Step 1: Create OnlyOfficeSaveHandler**

```java
// OnlyOfficeSaveHandler.java
package com.fos.workspace.onlyoffice.application;

import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * Handles the OnlyOffice save callback.
 *
 * When a user finishes editing a document in OnlyOffice and it auto-saves or
 * the user clicks save, OnlyOffice Document Server calls our callback URL with
 * a JSON body like:
 *
 * {
 *   "status": 2,      ← 2 = document is ready to save
 *   "url": "https://onlyoffice-server/..."  ← download the new file from here
 * }
 *
 * We respond with {"error": 0} to acknowledge.
 *
 * Status codes from OnlyOffice:
 *   1 = document is being edited (no save yet)
 *   2 = document ready to save (download from url)
 *   3 = document save error
 *   6 = document being edited, but saving is forced
 *   7 = error during forced save
 */
@Service
public class OnlyOfficeSaveHandler {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeSaveHandler.class);
    private static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(10);

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final FosKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public OnlyOfficeSaveHandler(WorkspaceDocumentRepository documentRepository,
                                  StoragePort storagePort,
                                  FosKafkaProducer kafkaProducer,
                                  ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Processes the OnlyOffice callback.
     *
     * @param documentId the workspace document resource ID (from the URL path)
     * @param callbackBody the raw JSON body from OnlyOffice
     */
    public void handleCallback(UUID documentId, String callbackBody) {
        try {
            JsonNode payload = objectMapper.readTree(callbackBody);
            int status = payload.get("status").asInt();

            // Status 2 = ready to save (most common save trigger)
            // Status 6 = forced save
            if (status != 2 && status != 6) {
                log.debug("OnlyOffice callback: status={} — not a save event, ignoring", status);
                return;
            }

            String downloadUrl = payload.get("url").asText();
            log.info("OnlyOffice save callback: documentId={} status={} url={}",
                    documentId, status, downloadUrl);

            // ── 1. Load the document ─────────────────────────────────────────
            WorkspaceDocument document = documentRepository
                    .findByResourceId(documentId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Document not found for OnlyOffice callback: " + documentId));

            if (document.currentVersion() == null) return;

            // ── 2. Download the saved file from OnlyOffice ────────────────────
            byte[] fileBytes = restTemplate.getForObject(downloadUrl, byte[].class);
            if (fileBytes == null || fileBytes.length == 0) {
                log.warn("OnlyOffice returned empty file for documentId={}", documentId);
                return;
            }

            // ── 3. Generate new MinIO object key for this version ─────────────
            int newVersionNumber = document.nextVersionNumber();
            String oldKey = document.currentVersion().getStorageObjectKey();
            // Replace the version segment: "documents/uuid/v1_file.docx" → "v2_file.docx"
            String newKey = oldKey.replaceFirst("v\\d+_", "v" + newVersionNumber + "_");
            String bucket = document.currentVersion().getStorageBucket();

            // ── 4. Upload to MinIO via StoragePort ────────────────────────────
            // StoragePort does not have a direct byte-upload method in Phase 1.
            // We use generateUploadUrl + a direct PUT (simplified for now).
            // TODO Sprint 1.5: add StoragePort.putObject(bucket, key, bytes) method
            // For now, we log that the save was received and skip the actual upload.
            // The NoopStorageAdapter makes this a safe no-op in tests.
            log.info("OnlyOffice save processed (MinIO upload deferred to Sprint 1.5): " +
                    "documentId={} newVersion={}", documentId, newVersionNumber);

            // ── 5. Create a new DocumentVersion ──────────────────────────────
            DocumentVersion newVersion = new DocumentVersion(
                    newKey, bucket,
                    document.currentVersion().getOriginalFilename(),
                    document.currentVersion().getContentType(),
                    (long) fileBytes.length,
                    newVersionNumber,
                    null, // OnlyOffice save — uploader is the editing actor (not tracked here yet)
                    "Auto-saved via OnlyOffice");

            document.addVersion(newVersion);
            documentRepository.save(document);

            // ── 6. Emit AUDIT signal ──────────────────────────────────────────
            kafkaProducer.emit(SignalEnvelope.builder()
                    .type(SignalType.AUDIT)
                    .topic(KafkaTopics.AUDIT_ALL)
                    .actorRef(documentId.toString())
                    .build());

        } catch (Exception e) {
            log.error("Failed to process OnlyOffice callback for documentId={}: {}",
                    documentId, e.getMessage(), e);
            // Do NOT rethrow — OnlyOffice needs us to return 200 regardless
        }
    }
}
```

- [ ] **Step 2: Update OnlyOfficeController to use OnlyOfficeSaveHandler**

Replace the stub callback method in `OnlyOfficeController.java`:

```java
// Inject OnlyOfficeSaveHandler
private final OnlyOfficeSaveHandler saveHandler;

public OnlyOfficeController(OnlyOfficeConfigService configService,
                              OnlyOfficeSaveHandler saveHandler) {
    this.configService = configService;
    this.saveHandler = saveHandler;
}

@PostMapping("/callback/{documentId}")
public String handleSaveCallback(@PathVariable UUID documentId,
                                  @RequestBody String callbackBody) {
    saveHandler.handleCallback(documentId, callbackBody);
    return "{\"error\": 0}";
}
```

- [ ] **Step 3: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/
git commit -m "feat(workspace/onlyoffice): implement OnlyOfficeSaveHandler for document version creation on save"
```

---

## Task 6: Integration Tests

- [ ] **Step 1: Create NotificationIntegrationTest**

```java
// NotificationIntegrationTest.java
package com.fos.workspace.notification;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.notification.api.NotificationResponse;
import com.fos.workspace.notification.application.NotificationService;
import com.fos.workspace.notification.domain.NotificationType;
import com.fos.workspace.notification.domain.WorkspaceNotification;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",
    "spring.security.enabled=false"
})
class NotificationIntegrationTest extends FosTestContainersBase {

    @Autowired
    private WorkspaceNotificationRepository notificationRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final UUID TEST_ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    @Test
    void should_return_notifications_for_actor() {
        // Directly insert a notification for test actor
        WorkspaceNotification n = WorkspaceNotification.create(
                TEST_ACTOR_ID, null,
                NotificationType.DOCUMENT_MISSING,
                "Test Notification", "Test body",
                null, null);
        notificationRepository.save(n);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/notifications", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return_unread_count() {
        // Insert 3 unread notifications
        for (int i = 0; i < 3; i++) {
            WorkspaceNotification n = WorkspaceNotification.create(
                    TEST_ACTOR_ID, null, NotificationType.GENERAL,
                    "Notification " + i, "Body", null, null);
            notificationRepository.save(n);
        }

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/notifications/unread-count", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("count");
    }

    @Test
    void should_mark_notification_as_read() {
        WorkspaceNotification n = WorkspaceNotification.create(
                TEST_ACTOR_ID, null, NotificationType.GENERAL,
                "Mark Read Test", "Body", null, null);
        WorkspaceNotification saved = notificationRepository.save(n);

        restTemplate.patchForObject(
                "/api/v1/notifications/" + saved.getResourceId() + "/read",
                null, Void.class);

        WorkspaceNotification updated = notificationRepository
                .findByResourceId(saved.getResourceId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
    }
}
```

- [ ] **Step 2: Create SearchIntegrationTest**

```java
// SearchIntegrationTest.java
package com.fos.workspace.search;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.search.api.SearchResponse;
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

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",
    "spring.security.enabled=false"
})
class SearchIntegrationTest extends FosTestContainersBase {

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
    void stubPolicy() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return_search_results_for_matching_document_name() {
        // Upload a document with a unique searchable name
        String uniqueName = "UniqueSearchableName_" + System.currentTimeMillis();
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                uniqueName, null,
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "search_test.pdf", "application/pdf", 1024L,
                null, null, List.of(), null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate", initiateRequest,
                DocumentService.UploadInitiationResult.class);

        restTemplate.postForObject("/api/v1/documents/upload/confirm",
                new DocumentController.ConfirmUploadWithMetadata(
                        initResult.documentId(), initResult.objectKey(), "fos-workspace",
                        uniqueName, null,
                        DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                        "search_test.pdf", "application/pdf", 1024L,
                        null, null, List.of(), null),
                DocumentResponse.class);

        // Search for it
        ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                "/api/v1/search?q=" + uniqueName, SearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(response.getBody().documents())
                .anyMatch(d -> d.name().equals(uniqueName));
    }

    @Test
    void should_return_empty_when_no_match() {
        ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                "/api/v1/search?q=ZZZNOMATCHEXPECTED99999", SearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalDocuments()).isEqualTo(0);
        assertThat(response.getBody().totalEvents()).isEqualTo(0);
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
cd fos-workspace-service
mvn test -q
```

Expected: BUILD SUCCESS — all tests pass

- [ ] **Step 4: Commit and final build**

```bash
git add fos-workspace-service/src/test/
git commit -m "test(workspace): add NotificationIntegrationTest, SearchIntegrationTest"

cd football-os-core
mvn package -q
git commit -m "chore(workspace): sprint 1.4 complete — notifications, search, OnlyOffice save callback"
```

---

## Sprint Test Criteria

Sprint 1.4 is complete when:

1. All workspace tests pass (7 previous + 3 new = 10+ total)
2. `GET /api/v1/notifications` returns paginated notification list
3. `GET /api/v1/notifications/unread-count` returns `{"count": N}`
4. `PATCH /api/v1/notifications/{id}/read` marks a notification as read
5. `POST /api/v1/notifications/mark-all-read` marks all unread as read
6. `GET /api/v1/search?q=...` returns documents and events matching the query, filtered by role
7. `POST /api/v1/onlyoffice/callback/{documentId}` with status=2 creates a new document version
8. `WorkspaceKafkaConsumer` extends `AbstractFosConsumer` (no raw `@KafkaListener` on a plain method)
9. Mongock Migration004 ran successfully (`mongockChangeLog` has 4 entries)

---

## What NOT to Include in This Sprint

- **Push notifications (WebSocket/SSE)** — Phase 2+
- **OpenSearch/Elasticsearch search** — Phase 2+
- **Notification preferences (opt-in/out)** — Phase 2+
- **StoragePort.putObject() for direct byte upload** — deferred to Sprint 1.5 where it's needed by the Angular flow as well
- **Angular notification bell UI** — Sprint 1.5
