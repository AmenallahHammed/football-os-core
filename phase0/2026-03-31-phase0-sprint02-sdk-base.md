# Phase 0 Sprint 0.2 — SDK Base Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `sdk-core`, `sdk-events`, `sdk-security`, and `sdk-storage` are built, unit-tested, and installed to the local Maven repository. All governance services and future domain services depend on these.

**Architecture:** Four library modules. No Spring Boot application context needed — these are plain Spring-managed beans and interfaces. SDK modules are published via `mvn install` and referenced as `${fos.sdk.version}` in consuming services. Build order is strict: `sdk-core` → `sdk-events` → `sdk-security` → `sdk-storage`.

**Tech Stack:** Java 21, Spring Boot 3.3.x auto-configuration, Spring Data JPA + MongoDB, Spring Kafka, Spring Security OAuth2 Resource Server, MinIO SDK 8.5.11, JUnit 5, Mockito

**Required Patterns This Sprint:**
- `[REQUIRED]` **State Pattern** — `ResourceStateHandler` family in `sdk-core`
- `[REQUIRED]` **Template Method** — `AbstractFosConsumer` in `sdk-events`
- `[REQUIRED]` **Adapter / Port** — `StoragePort` in `sdk-storage`
- `[REQUIRED]` **Null Object** — `NoopStorageAdapter` in `sdk-storage`
- `[RECOMMENDED]` **Decorator** — `FosKafkaProducer` enriches every signal envelope

---

## File Map

```
fos-sdk/
├── sdk-core/
│   ├── pom.xml                                         MODIFY — add JPA + MongoDB deps
│   └── src/
│       ├── main/java/com/fos/sdk/core/
│       │   ├── BaseEntity.java                         CREATE
│       │   ├── BaseDocument.java                       CREATE
│       │   ├── PageResponse.java                       CREATE
│       │   ├── ErrorResponse.java                      CREATE
│       │   ├── ResourceState.java                      CREATE
│       │   ├── FosRoles.java                           CREATE
│       │   └── state/
│       │       ├── ResourceStateHandler.java           CREATE — interface
│       │       ├── DraftStateHandler.java              CREATE
│       │       ├── ActiveStateHandler.java             CREATE
│       │       ├── ArchivedStateHandler.java           CREATE
│       │       └── ResourceStateHandlerFactory.java    CREATE
│       └── test/java/com/fos/sdk/core/
│           ├── ResourceStateHandlerTest.java           CREATE
│           └── BaseEntityTest.java                     CREATE
│
├── sdk-events/
│   ├── pom.xml                                         MODIFY — add Kafka dep
│   └── src/
│       ├── main/java/com/fos/sdk/events/
│       │   ├── SignalEnvelope.java                     CREATE
│       │   ├── SignalType.java                         CREATE
│       │   ├── KafkaTopics.java                        CREATE
│       │   ├── FosKafkaProducer.java                   CREATE — Decorator pattern
│       │   ├── AbstractFosConsumer.java                CREATE — Template Method pattern
│       │   └── RequestContext.java                     CREATE — thread-local correlation ID
│       └── test/java/com/fos/sdk/events/
│           ├── FosKafkaProducerTest.java               CREATE
│           └── AbstractFosConsumerTest.java            CREATE
│
├── sdk-security/
│   ├── pom.xml                                         MODIFY — add security deps
│   └── src/
│       ├── main/java/com/fos/sdk/security/
│       │   ├── FosSecurityContext.java                 CREATE
│       │   ├── FosJwtConverter.java                    CREATE
│       │   ├── Audited.java                            CREATE — annotation
│       │   └── AuditAspect.java                        CREATE — Spring AOP aspect
│       └── test/java/com/fos/sdk/security/
│           └── FosSecurityContextTest.java             CREATE
│
└── sdk-storage/
    ├── pom.xml                                         CREATE
    └── src/
        ├── main/java/com/fos/sdk/storage/
        │   ├── StoragePort.java                        CREATE
        │   ├── PresignedUploadUrl.java                 CREATE
        │   ├── StorageAutoConfiguration.java           CREATE
        │   └── adapter/
        │       ├── NoopStorageAdapter.java             CREATE
        │       ├── MinioStorageAdapter.java            CREATE
        │       ├── S3StorageAdapter.java               CREATE (stub)
        │       └── AzureBlobStorageAdapter.java        CREATE (stub)
        └── test/java/com/fos/sdk/storage/
            ├── NoopStorageAdapterTest.java             CREATE
            └── MinioStorageAdapterTest.java            CREATE
```

---

## Task 1: sdk-core — Dependencies + Base Types

**Files:**
- Modify: `fos-sdk/sdk-core/pom.xml`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/BaseEntity.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/BaseDocument.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/PageResponse.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/ErrorResponse.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/ResourceState.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/FosRoles.java`

- [ ] **Step 1: Update sdk-core pom.xml with real dependencies**

```xml
<!-- fos-sdk/sdk-core/pom.xml -->
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

- [ ] **Step 2: Write `ResourceState.java`**

```java
// src/main/java/com/fos/sdk/core/ResourceState.java
package com.fos.sdk.core;

public enum ResourceState {
    DRAFT, ACTIVE, ARCHIVED
}
```

- [ ] **Step 3: Write `FosRoles.java`**

```java
// src/main/java/com/fos/sdk/core/FosRoles.java
package com.fos.sdk.core;

public final class FosRoles {
    public static final String PLAYER           = "ROLE_PLAYER";
    public static final String HEAD_COACH       = "ROLE_HEAD_COACH";
    public static final String ASSISTANT_COACH  = "ROLE_ASSISTANT_COACH";
    public static final String PHYSICAL_COACH   = "ROLE_PHYSICAL_COACH";
    public static final String GOALKEEPER_COACH = "ROLE_GOALKEEPER_COACH";
    public static final String VIDEO_ANALYST    = "ROLE_VIDEO_ANALYST";
    public static final String DATA_ANALYST     = "ROLE_DATA_ANALYST";
    public static final String MEDICAL_STAFF    = "ROLE_MEDICAL_STAFF";
    public static final String CLUB_ADMIN       = "ROLE_CLUB_ADMIN";
    public static final String PLATFORM_ADMIN   = "ROLE_PLATFORM_ADMIN";

    private FosRoles() {}
}
```

- [ ] **Step 4: Write `BaseEntity.java`**

```java
// src/main/java/com/fos/sdk/core/BaseEntity.java
package com.fos.sdk.core;

import com.fos.sdk.core.state.ResourceStateHandler;
import com.fos.sdk.core.state.ResourceStateHandlerFactory;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceState state = ResourceState.DRAFT;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (resourceId == null) resourceId = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public ResourceStateHandler stateHandler() {
        return ResourceStateHandlerFactory.forState(state);
    }

    public void activate() {
        if (!stateHandler().canActivate()) {
            throw new IllegalStateException("Cannot activate resource in state: " + state);
        }
        this.state = ResourceState.ACTIVE;
    }

    public void archive() {
        if (!stateHandler().canArchive()) {
            throw new IllegalStateException("Cannot archive resource in state: " + state);
        }
        this.state = ResourceState.ARCHIVED;
    }

    // Getters (no setters for state — use activate/archive)
    public UUID getResourceId() { return resourceId; }
    public ResourceState getState() { return state; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 5: Write `BaseDocument.java`**

```java
// src/main/java/com/fos/sdk/core/BaseDocument.java
package com.fos.sdk.core;

import com.fos.sdk.core.state.ResourceStateHandler;
import com.fos.sdk.core.state.ResourceStateHandlerFactory;
import org.springframework.data.annotation.*;
import java.time.Instant;
import java.util.UUID;

public abstract class BaseDocument {

    @Id
    private String id;

    private UUID resourceId;
    private ResourceState state = ResourceState.DRAFT;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void initId() {
        if (resourceId == null) {
            resourceId = UUID.randomUUID();
            id = resourceId.toString();
        }
    }

    public ResourceStateHandler stateHandler() {
        return ResourceStateHandlerFactory.forState(state);
    }

    public void activate() {
        if (!stateHandler().canActivate()) {
            throw new IllegalStateException("Cannot activate document in state: " + state);
        }
        this.state = ResourceState.ACTIVE;
    }

    public void archive() {
        if (!stateHandler().canArchive()) {
            throw new IllegalStateException("Cannot archive document in state: " + state);
        }
        this.state = ResourceState.ARCHIVED;
    }

    public String getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public ResourceState getState() { return state; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 6: Write `PageResponse.java` and `ErrorResponse.java`**

```java
// src/main/java/com/fos/sdk/core/PageResponse.java
package com.fos.sdk.core;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
```

```java
// src/main/java/com/fos/sdk/core/ErrorResponse.java
package com.fos.sdk.core;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    String code,
    String message,
    List<String> details,
    Instant timestamp,
    String correlationId
) {
    public static ErrorResponse of(String code, String message, String correlationId) {
        return new ErrorResponse(code, message, List.of(), Instant.now(), correlationId);
    }

    public static ErrorResponse of(String code, String message, List<String> details, String correlationId) {
        return new ErrorResponse(code, message, details, Instant.now(), correlationId);
    }
}
```

- [ ] **Step 7: Build sdk-core (no tests yet)**

```bash
cd fos-sdk/sdk-core
mvn clean install -DskipTests
```

Expected: `BUILD SUCCESS`

---

## Task 2: sdk-core — State Pattern (REQUIRED)

**Files:**
- Create: `sdk-core/src/main/java/com/fos/sdk/core/state/ResourceStateHandler.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/state/DraftStateHandler.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/state/ActiveStateHandler.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/state/ArchivedStateHandler.java`
- Create: `sdk-core/src/main/java/com/fos/sdk/core/state/ResourceStateHandlerFactory.java`
- Create: `sdk-core/src/test/java/com/fos/sdk/core/ResourceStateHandlerTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/fos/sdk/core/ResourceStateHandlerTest.java
package com.fos.sdk.core;

import com.fos.sdk.core.state.ResourceStateHandlerFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResourceStateHandlerTest {

    @Test
    void should_allow_edit_and_activate_when_draft() {
        var handler = ResourceStateHandlerFactory.forState(ResourceState.DRAFT);
        assertThat(handler.canEdit()).isTrue();
        assertThat(handler.canActivate()).isTrue();
        assertThat(handler.canArchive()).isFalse();
        assertThat(handler.canShare()).isFalse();
    }

    @Test
    void should_allow_archive_and_share_when_active() {
        var handler = ResourceStateHandlerFactory.forState(ResourceState.ACTIVE);
        assertThat(handler.canEdit()).isFalse();
        assertThat(handler.canActivate()).isFalse();
        assertThat(handler.canArchive()).isTrue();
        assertThat(handler.canShare()).isTrue();
    }

    @Test
    void should_deny_all_transitions_when_archived() {
        var handler = ResourceStateHandlerFactory.forState(ResourceState.ARCHIVED);
        assertThat(handler.canEdit()).isFalse();
        assertThat(handler.canActivate()).isFalse();
        assertThat(handler.canArchive()).isFalse();
        assertThat(handler.canShare()).isFalse();
    }

    @Test
    void should_transition_draft_to_active() {
        var handler = ResourceStateHandlerFactory.forState(ResourceState.DRAFT);
        assertThat(handler.transitionTo(ResourceState.ACTIVE)).isEqualTo(ResourceState.ACTIVE);
    }

    @Test
    void should_reject_draft_to_archived_transition() {
        var handler = ResourceStateHandlerFactory.forState(ResourceState.DRAFT);
        assertThatThrownBy(() -> handler.transitionTo(ResourceState.ARCHIVED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition");
    }
}
```

- [ ] **Step 2: Run test — confirm it fails**

```bash
mvn test -pl fos-sdk/sdk-core -Dtest=ResourceStateHandlerTest
```

Expected: FAIL — `ResourceStateHandlerFactory` not found.

- [ ] **Step 3: Implement the State pattern**

```java
// src/main/java/com/fos/sdk/core/state/ResourceStateHandler.java
package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public interface ResourceStateHandler {
    boolean canEdit();
    boolean canActivate();
    boolean canArchive();
    boolean canShare();
    ResourceState transitionTo(ResourceState target);
}
```

```java
// src/main/java/com/fos/sdk/core/state/DraftStateHandler.java
package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public class DraftStateHandler implements ResourceStateHandler {
    public boolean canEdit()     { return true; }
    public boolean canActivate() { return true; }
    public boolean canArchive()  { return false; }
    public boolean canShare()    { return false; }

    public ResourceState transitionTo(ResourceState target) {
        if (target == ResourceState.ACTIVE) return ResourceState.ACTIVE;
        throw new IllegalStateException("Cannot transition from DRAFT to " + target);
    }
}
```

```java
// src/main/java/com/fos/sdk/core/state/ActiveStateHandler.java
package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public class ActiveStateHandler implements ResourceStateHandler {
    public boolean canEdit()     { return false; }
    public boolean canActivate() { return false; }
    public boolean canArchive()  { return true; }
    public boolean canShare()    { return true; }

    public ResourceState transitionTo(ResourceState target) {
        if (target == ResourceState.ARCHIVED) return ResourceState.ARCHIVED;
        throw new IllegalStateException("Cannot transition from ACTIVE to " + target);
    }
}
```

```java
// src/main/java/com/fos/sdk/core/state/ArchivedStateHandler.java
package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public class ArchivedStateHandler implements ResourceStateHandler {
    public boolean canEdit()     { return false; }
    public boolean canActivate() { return false; }
    public boolean canArchive()  { return false; }
    public boolean canShare()    { return false; }

    public ResourceState transitionTo(ResourceState target) {
        throw new IllegalStateException("Cannot transition from ARCHIVED to " + target);
    }
}
```

```java
// src/main/java/com/fos/sdk/core/state/ResourceStateHandlerFactory.java
package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public final class ResourceStateHandlerFactory {
    private ResourceStateHandlerFactory() {}

    public static ResourceStateHandler forState(ResourceState state) {
        return switch (state) {
            case DRAFT    -> new DraftStateHandler();
            case ACTIVE   -> new ActiveStateHandler();
            case ARCHIVED -> new ArchivedStateHandler();
        };
    }
}
```

- [ ] **Step 4: Run test — confirm it passes**

```bash
mvn test -pl fos-sdk/sdk-core -Dtest=ResourceStateHandlerTest
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Install sdk-core**

```bash
mvn install -pl fos-sdk/sdk-core
```

- [ ] **Step 6: Commit**

```bash
git add fos-sdk/sdk-core/
git commit -m "feat(sdk-core): add BaseEntity, BaseDocument, ResourceState, FosRoles, and State pattern handlers"
```

---

## Task 3: sdk-events — Signal Types + Kafka Infrastructure

**Files:**
- Modify: `fos-sdk/sdk-events/pom.xml`
- Create: `sdk-events/src/main/java/com/fos/sdk/events/SignalType.java`
- Create: `sdk-events/src/main/java/com/fos/sdk/events/SignalEnvelope.java`
- Create: `sdk-events/src/main/java/com/fos/sdk/events/KafkaTopics.java`
- Create: `sdk-events/src/main/java/com/fos/sdk/events/RequestContext.java`
- Create: `sdk-events/src/main/java/com/fos/sdk/events/FosKafkaProducer.java`
- Create: `sdk-events/src/main/java/com/fos/sdk/events/AbstractFosConsumer.java`
- Create: `sdk-events/src/test/java/com/fos/sdk/events/FosKafkaProducerTest.java`

- [ ] **Step 1: Update sdk-events pom.xml**

```xml
<!-- fos-sdk/sdk-events/pom.xml -->
<dependencies>
  <dependency>
    <groupId>com.fos</groupId>
    <artifactId>sdk-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

- [ ] **Step 2: Write `SignalType.java` and `KafkaTopics.java`**

```java
// src/main/java/com/fos/sdk/events/SignalType.java
package com.fos.sdk.events;

public enum SignalType {
    INTENT, FACT, ALERT, AUDIT
}
```

```java
// src/main/java/com/fos/sdk/events/KafkaTopics.java
package com.fos.sdk.events;

public final class KafkaTopics {
    // Identity
    public static final String IDENTITY_ACTOR_CREATED     = "fos.identity.actor.created";
    public static final String IDENTITY_ACTOR_DEACTIVATED = "fos.identity.actor.deactivated";
    public static final String IDENTITY_ROLE_ASSIGNED     = "fos.identity.actor.role-assigned";

    // Canonical
    public static final String CANONICAL_PLAYER_CREATED   = "fos.canonical.player.created";
    public static final String CANONICAL_PLAYER_UPDATED   = "fos.canonical.player.updated";
    public static final String CANONICAL_TEAM_CREATED     = "fos.canonical.team.created";
    public static final String CANONICAL_TEAM_UPDATED     = "fos.canonical.team.updated";

    // Storage (emitted by domain services when using sdk-storage)
    public static final String STORAGE_FILE_UPLOADED      = "fos.storage.file.uploaded";

    // Audit
    public static final String AUDIT_ALL                  = "fos.audit.all";

    // Signal service
    public static final String SIGNAL_ESCALATION_RAISED   = "fos.signal.escalation.raised";
    public static final String SIGNAL_ESCALATION_RESOLVED = "fos.signal.escalation.resolved";

    private KafkaTopics() {}
}
```

- [ ] **Step 3: Write `SignalEnvelope.java`**

```java
// src/main/java/com/fos/sdk/events/SignalEnvelope.java
package com.fos.sdk.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record SignalEnvelope(
    UUID signalId,
    SignalType type,
    String topic,
    JsonNode payload,
    String actorRef,
    String correlationId,
    Instant timestamp
) {
    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
            .signalId(signalId)
            .type(type)
            .topic(topic)
            .payload(payload)
            .actorRef(actorRef)
            .correlationId(correlationId)
            .timestamp(timestamp);
    }

    public static class Builder {
        private UUID signalId = UUID.randomUUID();
        private SignalType type;
        private String topic;
        private JsonNode payload;
        private String actorRef;
        private String correlationId;
        private Instant timestamp;

        public Builder signalId(UUID v)      { signalId = v;      return this; }
        public Builder type(SignalType v)     { type = v;          return this; }
        public Builder topic(String v)        { topic = v;         return this; }
        public Builder payload(JsonNode v)    { payload = v;       return this; }
        public Builder actorRef(String v)     { actorRef = v;      return this; }
        public Builder correlationId(String v){ correlationId = v; return this; }
        public Builder timestamp(Instant v)   { timestamp = v;     return this; }

        public SignalEnvelope build() {
            return new SignalEnvelope(signalId, type, topic, payload, actorRef, correlationId, timestamp);
        }
    }
}
```

- [ ] **Step 4: Write `RequestContext.java` — thread-local correlation ID holder**

```java
// src/main/java/com/fos/sdk/events/RequestContext.java
package com.fos.sdk.events;

public final class RequestContext {
    private static final ThreadLocal<String> correlationId = new InheritableThreadLocal<>();

    public static void set(String id)  { correlationId.set(id); }
    public static String get()         { return correlationId.get() != null ? correlationId.get() : "no-correlation-id"; }
    public static void clear()         { correlationId.remove(); }

    private RequestContext() {}
}
```

- [ ] **Step 5: Write the failing test for FosKafkaProducer**

```java
// src/test/java/com/fos/sdk/events/FosKafkaProducerTest.java
package com.fos.sdk.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FosKafkaProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private FosKafkaProducer producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        producer = new FosKafkaProducer(kafkaTemplate, objectMapper);
    }

    @Test
    void should_enrich_envelope_with_correlation_id_before_sending() {
        RequestContext.set("req-abc-123");
        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
            .actorRef("actor-001")
            .payload(objectMapper.createObjectNode().put("name", "test"))
            .build();

        producer.emit(envelope);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.IDENTITY_ACTOR_CREATED), captor.capture());
        assertThat(captor.getValue()).contains("req-abc-123");

        RequestContext.clear();
    }

    @Test
    void should_use_fallback_correlation_id_when_context_is_empty() {
        RequestContext.clear();
        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
            .payload(objectMapper.createObjectNode())
            .build();

        producer.emit(envelope);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.IDENTITY_ACTOR_CREATED), captor.capture());
        assertThat(captor.getValue()).contains("no-correlation-id");
    }
}
```

- [ ] **Step 6: Run test — confirm it fails**

```bash
mvn test -pl fos-sdk/sdk-events -Dtest=FosKafkaProducerTest
```

Expected: FAIL — `FosKafkaProducer` not found.

- [ ] **Step 7: Implement `FosKafkaProducer` (Decorator pattern)**

```java
// src/main/java/com/fos/sdk/events/FosKafkaProducer.java
package com.fos.sdk.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * Decorator around KafkaTemplate. Transparently enriches every SignalEnvelope
 * with correlationId and timestamp before sending. Domain code never touches
 * KafkaTemplate directly.
 */
@Component
public class FosKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(FosKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FosKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void emit(SignalEnvelope envelope) {
        // Decorator: inject cross-cutting fields transparently
        SignalEnvelope enriched = envelope.toBuilder()
            .correlationId(RequestContext.get())
            .timestamp(Instant.now())
            .build();

        try {
            String json = objectMapper.writeValueAsString(enriched);
            kafkaTemplate.send(enriched.topic(), json);
            log.debug("Emitted {} signal on topic {}, correlationId={}",
                enriched.type(), enriched.topic(), enriched.correlationId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SignalEnvelope for topic: " + enriched.topic(), e);
        }
    }
}
```

- [ ] **Step 8: Run test — confirm it passes**

```bash
mvn test -pl fos-sdk/sdk-events -Dtest=FosKafkaProducerTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

---

## Task 4: sdk-events — AbstractFosConsumer (Template Method, REQUIRED)

**Files:**
- Create: `sdk-events/src/main/java/com/fos/sdk/events/AbstractFosConsumer.java`
- Create: `sdk-events/src/test/java/com/fos/sdk/events/AbstractFosConsumerTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/fos/sdk/events/AbstractFosConsumerTest.java
package com.fos.sdk.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class AbstractFosConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_call_handle_with_deserialized_envelope() throws Exception {
        var received = new AtomicReference<SignalEnvelope>();

        AbstractFosConsumer consumer = new AbstractFosConsumer(objectMapper) {
            @Override
            protected void handle(SignalEnvelope envelope) {
                received.set(envelope);
            }
        };

        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
            .correlationId("corr-001")
            .payload(objectMapper.createObjectNode().put("actorId", "a-001"))
            .build();

        String json = objectMapper.writeValueAsString(envelope);
        var record = new ConsumerRecord<>(KafkaTopics.IDENTITY_ACTOR_CREATED, 0, 0L, "key", json);

        consumer.onMessage(record);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().correlationId()).isEqualTo("corr-001");
        assertThat(received.get().type()).isEqualTo(SignalType.FACT);
    }

    @Test
    void should_not_throw_when_handle_raises_exception() throws Exception {
        AbstractFosConsumer consumer = new AbstractFosConsumer(objectMapper) {
            @Override
            protected void handle(SignalEnvelope envelope) {
                throw new RuntimeException("domain error");
            }
        };

        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic("test.topic")
            .payload(objectMapper.createObjectNode())
            .build();

        String json = objectMapper.writeValueAsString(envelope);
        var record = new ConsumerRecord<>("test.topic", 0, 0L, "key", json);

        // Should not propagate — error handling is the base class responsibility
        assertThatCode(() -> consumer.onMessage(record)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test — confirm it fails**

```bash
mvn test -pl fos-sdk/sdk-events -Dtest=AbstractFosConsumerTest
```

Expected: FAIL — `AbstractFosConsumer` not found.

- [ ] **Step 3: Implement `AbstractFosConsumer`**

```java
// src/main/java/com/fos/sdk/events/AbstractFosConsumer.java
package com.fos.sdk.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Template Method base for all Kafka consumers in FOS.
 * Handles deserialization, correlation ID propagation, error logging,
 * and offset acknowledgement. Subclasses implement only domain logic.
 */
public abstract class AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AbstractFosConsumer.class);

    private final ObjectMapper objectMapper;

    protected AbstractFosConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Template method — final: structure is fixed
    public final void onMessage(ConsumerRecord<String, String> record) {
        SignalEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(record.value(), SignalEnvelope.class);
            MDC.put("correlationId", envelope.correlationId() != null ? envelope.correlationId() : "unknown");
            RequestContext.set(envelope.correlationId());

            log.debug("Received {} signal on topic {}", envelope.type(), record.topic());

            handle(envelope);   // subclass implements this step only

        } catch (Exception e) {
            handleError(record, envelope, e);
        } finally {
            MDC.clear();
            RequestContext.clear();
        }
    }

    // The only method subclasses implement
    protected abstract void handle(SignalEnvelope envelope);

    // Override in subclass if custom error handling is needed
    protected void handleError(ConsumerRecord<String, String> record, SignalEnvelope envelope, Exception e) {
        log.error("Error processing signal on topic={}, offset={}, error={}",
            record.topic(), record.offset(), e.getMessage(), e);
    }
}
```

- [ ] **Step 4: Run test — confirm it passes**

```bash
mvn test -pl fos-sdk/sdk-events -Dtest=AbstractFosConsumerTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Install sdk-events**

```bash
mvn install -pl fos-sdk/sdk-events
```

- [ ] **Step 6: Commit**

```bash
git add fos-sdk/sdk-events/
git commit -m "feat(sdk-events): add SignalEnvelope, KafkaTopics, FosKafkaProducer (Decorator), AbstractFosConsumer (Template Method)"
```

---

## Task 5: sdk-security — JWT Context + Audit

**Files:**
- Modify: `fos-sdk/sdk-security/pom.xml`
- Create: `sdk-security/src/main/java/com/fos/sdk/security/FosSecurityContext.java`
- Create: `sdk-security/src/main/java/com/fos/sdk/security/FosJwtConverter.java`
- Create: `sdk-security/src/main/java/com/fos/sdk/security/Audited.java`
- Create: `sdk-security/src/main/java/com/fos/sdk/security/AuditAspect.java`
- Create: `sdk-security/src/test/java/com/fos/sdk/security/FosSecurityContextTest.java`

- [ ] **Step 1: Update sdk-security pom.xml**

```xml
<!-- fos-sdk/sdk-security/pom.xml -->
<dependencies>
  <dependency>
    <groupId>com.fos</groupId>
    <artifactId>sdk-core</artifactId>
  </dependency>
  <dependency>
    <groupId>com.fos</groupId>
    <artifactId>sdk-events</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

- [ ] **Step 2: Write the failing test**

```java
// src/test/java/com/fos/sdk/security/FosSecurityContextTest.java
package com.fos.sdk.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class FosSecurityContextTest {

    @Test
    void should_extract_actor_id_from_jwt() {
        String actorId = UUID.randomUUID().toString();
        setUpSecurityContext(actorId, "test-club-001", List.of("ROLE_HEAD_COACH"));

        assertThat(FosSecurityContext.actorId()).isEqualTo(actorId);
    }

    @Test
    void should_extract_club_id_from_jwt() {
        setUpSecurityContext("actor-001", "club-999", List.of("ROLE_PLAYER"));

        assertThat(FosSecurityContext.clubId()).isEqualTo("club-999");
    }

    @Test
    void should_return_true_when_actor_has_role() {
        setUpSecurityContext("actor-001", "club-001", List.of("ROLE_HEAD_COACH", "ROLE_ASSISTANT_COACH"));

        assertThat(FosSecurityContext.hasRole("ROLE_HEAD_COACH")).isTrue();
        assertThat(FosSecurityContext.hasRole("ROLE_PLAYER")).isFalse();
    }

    @Test
    void should_throw_when_no_security_context() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(FosSecurityContext::actorId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated actor");
    }

    private void setUpSecurityContext(String actorId, String clubId, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", actorId)
            .claim("fos_club_id", clubId)
            .claim("roles", roles)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        var auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
```

- [ ] **Step 3: Run test — confirm it fails**

```bash
mvn test -pl fos-sdk/sdk-security -Dtest=FosSecurityContextTest
```

Expected: FAIL — `FosSecurityContext` not found.

- [ ] **Step 4: Implement `FosSecurityContext.java`**

```java
// src/main/java/com/fos/sdk/security/FosSecurityContext.java
package com.fos.sdk.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;

/**
 * The only way to get the current actor in a request context.
 * Never parse the JWT manually — always use this class.
 */
public final class FosSecurityContext {

    private FosSecurityContext() {}

    public static String actorId() {
        return jwt().getSubject();
    }

    public static String clubId() {
        return jwt().getClaimAsString("fos_club_id");
    }

    @SuppressWarnings("unchecked")
    public static List<String> roles() {
        Object roles = jwt().getClaim("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public static boolean hasRole(String role) {
        return roles().contains(role);
    }

    private static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new IllegalStateException("No authenticated actor in security context");
    }
}
```

- [ ] **Step 5: Implement `FosJwtConverter.java`**

```java
// src/main/java/com/fos/sdk/security/FosJwtConverter.java
package com.fos.sdk.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Collection;
import java.util.List;

/**
 * Converts a Keycloak JWT into a Spring Security authentication token.
 * Extracts FOS roles from the "roles" claim.
 */
public class FosJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<SimpleGrantedAuthority> authorities = extractRoles(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    @SuppressWarnings("unchecked")
    private Collection<SimpleGrantedAuthority> extractRoles(Jwt jwt) {
        Object rolesClaim = jwt.getClaim("roles");
        if (!(rolesClaim instanceof List<?> roles)) return List.of();
        return roles.stream()
            .filter(String.class::isInstance)
            .map(r -> new SimpleGrantedAuthority((String) r))
            .toList();
    }
}
```

- [ ] **Step 6: Implement `@Audited` annotation and `AuditAspect`**

```java
// src/main/java/com/fos/sdk/security/Audited.java
package com.fos.sdk.security;

import java.lang.annotation.*;

/**
 * Marks a service method for automatic audit signal emission.
 * The AuditAspect intercepts annotated methods and emits a AUDIT SignalEnvelope to Kafka.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {
    String action();              // e.g. "workspace.file.read"
    String resourceType() default "";
}
```

```java
// src/main/java/com/fos/sdk/security/AuditAspect.java
package com.fos.sdk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final FosKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;

    public AuditAspect(FosKafkaProducer kafkaProducer, ObjectMapper objectMapper) {
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result = pjp.proceed();
        try {
            String actorId = FosSecurityContext.actorId();
            var payload = objectMapper.createObjectNode()
                .put("action", audited.action())
                .put("resourceType", audited.resourceType())
                .put("actorId", actorId);

            kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(actorId)
                .payload(payload)
                .build());
        } catch (Exception e) {
            log.warn("Failed to emit audit signal for action={}: {}", audited.action(), e.getMessage());
            // Audit failure must never break the business operation
        }
        return result;
    }
}
```

- [ ] **Step 7: Run all sdk-security tests**

```bash
mvn test -pl fos-sdk/sdk-security
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 8: Install all three SDK modules**

```bash
mvn install -pl fos-sdk/sdk-core,fos-sdk/sdk-events,fos-sdk/sdk-security
```

- [ ] **Step 9: Commit**

```bash
git add fos-sdk/sdk-security/
git commit -m "feat(sdk-security): add FosSecurityContext, FosJwtConverter, @Audited, AuditAspect"
```

---

## Sprint 0.2 Done — Verification Checklist

- [ ] `mvn test -pl fos-sdk/sdk-core` passes: ResourceStateHandlerTest (5 tests)
- [ ] `mvn test -pl fos-sdk/sdk-events` passes: FosKafkaProducerTest (2), AbstractFosConsumerTest (2)
- [ ] `mvn test -pl fos-sdk/sdk-security` passes: FosSecurityContextTest (4)
- [ ] `mvn install` from root builds all 3 SDK modules successfully
- [ ] State pattern: no `if (state == DRAFT)` in any code — all state checks go through `ResourceStateHandler`
- [ ] No raw `KafkaTemplate` calls in any consumer — all extend `AbstractFosConsumer`
- [ ] `FosSecurityContext` is the only actor-extraction mechanism — no JWT parsing elsewhere

**Next:** Sprint 0.3 — fos-governance-service (identity + canonical packages) + sdk-canonical

---

## Amendment B — Issues #1, #2, #3: Schema Versioning + Idempotency + DLQ

### B.1 Issue #1 — Add `schemaVersion` to SignalEnvelope (sdk-events)

**File:** `fos-sdk/sdk-events/src/main/java/com/fos/sdk/events/SignalEnvelope.java`

Add field `int schemaVersion` with default value `1`. This enables consumer-side upcasting when the envelope structure changes in a future sprint.

Replace the `SignalEnvelope` record with:

```java
public record SignalEnvelope(
    UUID signalId,
    SignalType type,
    String topic,
    JsonNode payload,
    String actorRef,
    String correlationId,
    Instant timestamp,
    int schemaVersion          // NEW — default 1; increment on breaking envelope changes
) {
    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
            .signalId(signalId).type(type).topic(topic).payload(payload)
            .actorRef(actorRef).correlationId(correlationId)
            .timestamp(timestamp).schemaVersion(schemaVersion);
    }

    public static class Builder {
        private UUID signalId = UUID.randomUUID();
        private SignalType type;
        private String topic;
        private JsonNode payload;
        private String actorRef;
        private String correlationId;
        private Instant timestamp;
        private int schemaVersion = 1;    // default

        public Builder signalId(UUID v)       { signalId = v;       return this; }
        public Builder type(SignalType v)      { type = v;           return this; }
        public Builder topic(String v)         { topic = v;          return this; }
        public Builder payload(JsonNode v)     { payload = v;        return this; }
        public Builder actorRef(String v)      { actorRef = v;       return this; }
        public Builder correlationId(String v) { correlationId = v;  return this; }
        public Builder timestamp(Instant v)    { timestamp = v;      return this; }
        public Builder schemaVersion(int v)    { schemaVersion = v;  return this; }

        public SignalEnvelope build() {
            return new SignalEnvelope(signalId, type, topic, payload,
                actorRef, correlationId, timestamp, schemaVersion);
        }
    }
}
```

Add a test to `FosKafkaProducerTest`:
```java
@Test
void should_default_schema_version_to_1() {
    // ... emit envelope, capture JSON, assert schemaVersion == 1
}
```

### B.2 Issue #2 — Idempotency Strategy in AbstractFosConsumer (sdk-events)

**Rule:** Every `AbstractFosConsumer` subclass must be idempotent — reprocessing a signal that was already handled must be a no-op.

**Strategy:** Consumer subclasses check whether the `signalId` was already processed before doing any work. The SDK provides a `ProcessedSignalTracker` interface; each service implements it with a DB-backed store.

Add to `sdk-events`:

```java
// src/main/java/com/fos/sdk/events/ProcessedSignalTracker.java
package com.fos.sdk.events;

import java.util.UUID;

/**
 * Idempotency contract for Kafka consumers.
 * Implementations persist processed signal IDs to prevent duplicate processing
 * on consumer restarts or rebalances.
 */
public interface ProcessedSignalTracker {
    boolean isAlreadyProcessed(UUID signalId);
    void markAsProcessed(UUID signalId);
}
```

Update `AbstractFosConsumer` to accept an optional tracker:

```java
public abstract class AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AbstractFosConsumer.class);
    private final ObjectMapper objectMapper;
    private final ProcessedSignalTracker tracker;  // nullable — opt-in per consumer

    protected AbstractFosConsumer(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    protected AbstractFosConsumer(ObjectMapper objectMapper, ProcessedSignalTracker tracker) {
        this.objectMapper = objectMapper;
        this.tracker = tracker;
    }

    public final void onMessage(ConsumerRecord<String, String> record) {
        SignalEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(record.value(), SignalEnvelope.class);
            MDC.put("correlationId", envelope.correlationId() != null ? envelope.correlationId() : "unknown");
            RequestContext.set(envelope.correlationId());

            // Idempotency check — skip if already processed
            if (tracker != null && tracker.isAlreadyProcessed(envelope.signalId())) {
                log.debug("Skipping already-processed signal: {}", envelope.signalId());
                return;
            }

            handle(envelope);

            // Mark as processed after successful handling
            if (tracker != null) {
                tracker.markAsProcessed(envelope.signalId());
            }

        } catch (Exception e) {
            handleError(record, envelope, e);
        } finally {
            MDC.clear();
            RequestContext.clear();
        }
    }

    protected abstract void handle(SignalEnvelope envelope);

    protected void handleError(ConsumerRecord<String, String> record, SignalEnvelope envelope, Exception e) {
        log.error("Error processing signal on topic={}, offset={}: {}",
            record.topic(), record.offset(), e.getMessage(), e);
        // Subclasses override to send to DLQ (see B.3)
    }
}
```

### B.3 Issue #3 — Dead Letter Queue in AbstractFosConsumer

Add a `DlqPublisher` component to `sdk-events`. Consumers that override `handleError()` use it to route poison messages to the `.dlq` topic instead of blocking forever.

```java
// src/main/java/com/fos/sdk/events/DlqPublisher.java
package com.fos.sdk.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends failed/malformed messages to their Dead Letter Queue topic.
 * DLQ topic convention: {original-topic}.dlq
 */
@Component
public class DlqPublisher {

    private static final Logger log = LoggerFactory.getLogger(DlqPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    public DlqPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendToDlq(ConsumerRecord<String, String> record, Exception cause) {
        String dlqTopic = record.topic() + ".dlq";
        log.warn("Sending poisoned message to DLQ: topic={}, offset={}, error={}",
            record.topic(), record.offset(), cause.getMessage());
        kafkaTemplate.send(dlqTopic, record.key(), record.value());
    }
}
```

Update `KafkaTopics` to document DLQ naming:

```java
// Add to KafkaTopics.java
// DLQ convention: any topic + ".dlq" suffix
// Examples:
//   fos.identity.actor.created.dlq
//   fos.canonical.player.created.dlq
//   fos.audit.all.dlq
// DLQ topics are auto-created by Kafka. Monitor them in production for poisoned messages.
public static final String DLQ_SUFFIX = ".dlq";
public static String dlqFor(String topic) { return topic + DLQ_SUFFIX; }
```

Add test to `AbstractFosConsumerTest`:
```java
@Test
void should_not_throw_and_allow_dlq_routing_when_handle_raises_exception() {
    // Verify handleError is called; subclass can override to use DlqPublisher
}
```

---

## Task 4: sdk-storage — StoragePort + Adapters (NEW)

**Files:**
- Create: `fos-sdk/sdk-storage/pom.xml`
- Create: `sdk-storage/src/main/java/com/fos/sdk/storage/StoragePort.java`
- Create: `sdk-storage/src/main/java/com/fos/sdk/storage/PresignedUploadUrl.java`
- Create: `sdk-storage/src/main/java/com/fos/sdk/storage/StorageAutoConfiguration.java`
- Create: all adapter classes

- [ ] **Step 1: Create sdk-storage pom.xml**

```xml
<!-- fos-sdk/sdk-storage/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.fos</groupId>
    <artifactId>fos-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>sdk-storage</artifactId>
  <name>FOS SDK — Storage</name>

  <dependencies>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
      <groupId>io.minio</groupId>
      <artifactId>minio</artifactId>
      <version>8.5.11</version>
      <optional>true</optional>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Create StoragePort and PresignedUploadUrl**

```java
// StoragePort.java
package com.fos.sdk.storage;

import java.time.Duration;

public interface StoragePort {
    PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                         String contentType, Duration expiry);
    String generateDownloadUrl(String bucket, String objectKey, Duration expiry);
    void confirmUpload(String bucket, String objectKey);
    void deleteObject(String bucket, String objectKey);
}
```

```java
// PresignedUploadUrl.java
package com.fos.sdk.storage;

import java.time.Instant;

public record PresignedUploadUrl(String uploadUrl, String objectKey, Instant expiresAt) {}
```

- [ ] **Step 3: Create NoopStorageAdapter (Null Object — default)**

```java
// NoopStorageAdapter.java
package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnMissingBean(StoragePort.class)
public class NoopStorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(NoopStorageAdapter.class);

    @Override
    public PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                                String contentType, Duration expiry) {
        log.info("[NOOP-STORAGE] generateUploadUrl: bucket={}, key={}", bucket, objectKey);
        return new PresignedUploadUrl(
            "https://noop.fos.local/upload/" + objectKey,
            objectKey,
            Instant.now().plus(expiry)
        );
    }

    @Override
    public String generateDownloadUrl(String bucket, String objectKey, Duration expiry) {
        log.info("[NOOP-STORAGE] generateDownloadUrl: bucket={}, key={}", bucket, objectKey);
        return "https://noop.fos.local/download/" + objectKey;
    }

    @Override
    public void confirmUpload(String bucket, String objectKey) {
        log.info("[NOOP-STORAGE] confirmUpload: bucket={}, key={}", bucket, objectKey);
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        log.info("[NOOP-STORAGE] deleteObject: bucket={}, key={}", bucket, objectKey);
    }
}
```

- [ ] **Step 4: Create MinioStorageAdapter**

```java
// MinioStorageAdapter.java
package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import io.minio.*;
import io.minio.http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "fos.storage.provider", havingValue = "minio")
public class MinioStorageAdapter implements StoragePort {

    private final MinioClient minioClient;
    private final String defaultBucket;

    public MinioStorageAdapter(
            @org.springframework.beans.factory.annotation.Value("${minio.endpoint:http://localhost:9000}") String endpoint,
            @org.springframework.beans.factory.annotation.Value("${minio.access-key:minioadmin}") String accessKey,
            @org.springframework.beans.factory.annotation.Value("${minio.secret-key:minioadmin}") String secretKey,
            @org.springframework.beans.factory.annotation.Value("${minio.bucket:fos-files}") String bucket) {
        this.minioClient = MinioClient.builder()
            .endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.defaultBucket = bucket;
    }

    @Override
    public PresignedUploadUrl generateUploadUrl(String bucket, String objectKey,
                                                String contentType, Duration expiry) {
        try {
            ensureBucket(bucket);
            String url = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT).bucket(bucket).object(objectKey)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS).build());
            return new PresignedUploadUrl(url, objectKey, Instant.now().plus(expiry));
        } catch (Exception e) {
            throw new IllegalStateException("MinIO upload URL generation failed for: " + objectKey, e);
        }
    }

    @Override
    public String generateDownloadUrl(String bucket, String objectKey, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectKey)
                    .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO download URL generation failed for: " + objectKey, e);
        }
    }

    @Override
    public void confirmUpload(String bucket, String objectKey) { /* no-op */ }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO delete failed for: " + objectKey, e);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
```

- [ ] **Step 5: Create S3 and Azure stubs**

```java
// S3StorageAdapter.java — stub
package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "fos.storage.provider", havingValue = "s3")
public class S3StorageAdapter implements StoragePort {
    @Override public PresignedUploadUrl generateUploadUrl(String b, String k, String c, Duration e) { throw new UnsupportedOperationException("S3StorageAdapter not yet implemented"); }
    @Override public String generateDownloadUrl(String b, String k, Duration e) { throw new UnsupportedOperationException(); }
    @Override public void confirmUpload(String b, String k) { throw new UnsupportedOperationException(); }
    @Override public void deleteObject(String b, String k) { throw new UnsupportedOperationException(); }
}
```

```java
// AzureBlobStorageAdapter.java — stub
package com.fos.sdk.storage.adapter;

import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "fos.storage.provider", havingValue = "azure")
public class AzureBlobStorageAdapter implements StoragePort {
    @Override public PresignedUploadUrl generateUploadUrl(String b, String k, String c, Duration e) { throw new UnsupportedOperationException("AzureBlobStorageAdapter not yet implemented"); }
    @Override public String generateDownloadUrl(String b, String k, Duration e) { throw new UnsupportedOperationException(); }
    @Override public void confirmUpload(String b, String k) { throw new UnsupportedOperationException(); }
    @Override public void deleteObject(String b, String k) { throw new UnsupportedOperationException(); }
}
```

- [ ] **Step 6: Create StorageAutoConfiguration**

```java
// StorageAutoConfiguration.java
package com.fos.sdk.storage;

import com.fos.sdk.storage.adapter.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({NoopStorageAdapter.class, MinioStorageAdapter.class,
         S3StorageAdapter.class, AzureBlobStorageAdapter.class})
public class StorageAutoConfiguration {}
```

Register in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.fos.sdk.storage.StorageAutoConfiguration
```

- [ ] **Step 7: Write tests**

```java
// NoopStorageAdapterTest.java
package com.fos.sdk.storage;

import com.fos.sdk.storage.adapter.NoopStorageAdapter;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class NoopStorageAdapterTest {
    private final StoragePort adapter = new NoopStorageAdapter();

    @Test
    void should_return_fake_upload_url_without_any_external_call() {
        var result = adapter.generateUploadUrl("fos-files", "test/doc.pdf",
            "application/pdf", Duration.ofMinutes(15));
        assertThat(result.uploadUrl()).startsWith("https://noop.fos.local/");
        assertThat(result.objectKey()).isEqualTo("test/doc.pdf");
    }

    @Test
    void should_not_throw_on_confirm_or_delete() {
        assertThatCode(() -> adapter.confirmUpload("bucket", "key")).doesNotThrowAnyException();
        assertThatCode(() -> adapter.deleteObject("bucket", "key")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 8: Build and install sdk-storage**

```bash
cd fos-sdk
mvn install -pl sdk-storage -am
```

Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add fos-sdk/sdk-storage/
git commit -m "feat(sdk-storage): add StoragePort, NoopStorageAdapter, MinioStorageAdapter, S3/Azure stubs"
```

---

## Sprint 0.2 Done — Verification Checklist

- [ ] `mvn install -pl sdk-core,sdk-events,sdk-security,sdk-storage -am` succeeds
- [ ] All unit tests pass: `sdk-core` (State pattern), `sdk-events` (Template Method), `sdk-security`, `sdk-storage` (Adapter/Port)
- [ ] `NoopStorageAdapter` is the fallback when `fos.storage.provider` is unset
- [ ] `MinioStorageAdapter` activates when `fos.storage.provider=minio`
- [ ] No vendor SDK classes (`io.minio.*`, `software.amazon.awssdk.*`) are imported outside `sdk-storage`
- [ ] `BaseEntity` and `BaseDocument` use `ResourceStateHandler` — no `if (state == ...)` checks
- [ ] `AbstractFosConsumer` enforces Kafka consumer structure
- [ ] `FosSecurityContext` is the only way to get actor from JWT

**Next:** Sprint 0.3 — fos-governance-service (identity + canonical packages) + sdk-canonical
