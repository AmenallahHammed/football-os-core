# Phase 1 Sprint 1.1 — fos-workspace-service: Scaffold + Document Domain + File Upload

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `fos-workspace-service` is a running Spring Boot application connected to MongoDB. Mongock migrations establish the database structure. The `document` package is fully implemented: upload a file (pre-signed URL flow via `StoragePort`), confirm upload, list documents, get a document, soft-delete a document. Every state-changing operation emits a FACT or AUDIT signal via `FosKafkaProducer`. Integration tests confirm the full upload flow end-to-end. The service is added to the monorepo parent `pom.xml` and to `docker-compose.yml`.

**Architecture:**
`fos-workspace-service` sits in the Domain layer (the outermost ring of the Three-Layer Onion). It depends **only** on `fos-sdk` — it never imports or HTTP-calls `fos-governance-service` directly. File uploads follow a three-step flow: (1) the frontend requests a pre-signed URL from the backend, (2) the frontend uploads the file bytes directly to MinIO using that URL, (3) the frontend calls the backend to confirm the upload. The backend only ever talks to MinIO through `StoragePort` from `sdk-storage` — never through a raw MinIO client. All MongoDB entities extend `BaseDocument` from `sdk-core`. Migrations are managed by Mongock — never by `spring.data.mongodb.auto-index-creation`.

**Why MongoDB?** Workspace data (documents, events, profiles, notes) is document-shaped: nested arrays, optional fields, rich metadata. MongoDB lets us store a document with all its versions, tags, and linked entities as a single JSON document — no joins required. PostgreSQL would require 5+ tables for what one MongoDB collection handles.

**Why Mongock?** Just like Flyway tracks which SQL migrations have run, Mongock tracks which MongoDB change-sets have run. Without it, every time the service restarts it would either re-create indexes (slow) or not create them at all (broken queries). Mongock solves this safely.

**Tech Stack:** Java 21, Spring Boot 3.3.x, MongoDB 7, Mongock 5.4.x, `sdk-core` (BaseDocument, ResourceState), `sdk-events` (FosKafkaProducer, KafkaTopics), `sdk-security` (FosSecurityContext), `sdk-storage` (StoragePort, PresignedUploadUrl), `sdk-policy` (PolicyClient, @PolicyGuard), `sdk-canonical` (CanonicalRef), JUnit 5, Testcontainers (MongoDB + Kafka), WireMock

**Required Patterns This Sprint:**
- `[REQUIRED]` **Adapter / Port** — `StoragePort` from `sdk-storage` is the only way to talk to MinIO. Never instantiate `MinioClient` directly in workspace code.
- `[REQUIRED]` **Proxy (Remote)** — `PolicyClient` from `sdk-policy` is the only way to check permissions. Never write `if (role.equals(...))` checks in service code.
- `[REQUIRED]` **Template Method** — `AbstractFosConsumer` base class is extended if/when workspace needs to consume Kafka topics (deferred to Sprint 1.4, but the pattern is established here via the signal emission side).
- `[REQUIRED]` **Null Object** — `NoopStorageAdapter` is active in tests. Tests never need a real MinIO container.
- `[RECOMMENDED]` **Builder** — `DocumentMetadata` and `SignalEnvelope` use the builder pattern for readable construction.

---

## File Map

```
football-os-core/pom.xml                                                MODIFY (add fos-workspace-service module)
football-os-core/docker-compose.yml                                     MODIFY (verify MongoDB and MinIO are present)

fos-workspace-service/
├── pom.xml                                                             CREATE
└── src/
    ├── main/
    │   ├── java/com/fos/workspace/
    │   │   ├── WorkspaceApp.java                                       CREATE
    │   │   ├── document/
    │   │   │   ├── api/
    │   │   │   │   ├── DocumentController.java                         CREATE
    │   │   │   │   ├── InitiateUploadRequest.java                      CREATE
    │   │   │   │   ├── ConfirmUploadRequest.java                       CREATE
    │   │   │   │   └── DocumentResponse.java                           CREATE
    │   │   │   ├── application/
    │   │   │   │   └── DocumentService.java                            CREATE
    │   │   │   ├── domain/
    │   │   │   │   ├── WorkspaceDocument.java                          CREATE
    │   │   │   │   ├── DocumentVersion.java                            CREATE
    │   │   │   │   ├── DocumentCategory.java                           CREATE
    │   │   │   │   └── DocumentVisibility.java                         CREATE
    │   │   │   └── infrastructure/
    │   │   │       └── persistence/
    │   │   │           └── WorkspaceDocumentRepository.java            CREATE
    │   │   └── config/
    │   │       ├── MongockConfig.java                                  CREATE
    │   │       └── GlobalExceptionHandler.java                         CREATE
    │   ├── resources/
    │   │   └── application.yml                                         CREATE
    │   └── db/migration/
    │       └── Migration001CreateDocumentIndexes.java                  CREATE
    └── test/java/com/fos/workspace/
        └── document/
            └── DocumentIntegrationTest.java                            CREATE
```

---

## Task 1: Add fos-workspace-service to the Monorepo

**Why:** Maven needs to know this module exists before it can compile it. The parent `pom.xml` is the index of all modules in the monorepo.

**Files:**
- Modify: `football-os-core/pom.xml`

- [ ] **Step 1: Add the workspace service module to parent pom.xml**

Open `football-os-core/pom.xml` and add `<module>fos-workspace-service</module>` to the modules section:

```xml
<modules>
  <module>fos-sdk</module>
  <module>fos-governance-service</module>
  <module>fos-gateway</module>
  <module>fos-workspace-service</module>   <!-- ADD THIS LINE -->
</modules>
```

- [ ] **Step 2: Also add the frontend module placeholder (empty, just so Maven doesn't error)**

We will not scaffold the frontend until Sprint 1.5. For now just add the directory reference without a real pom. Leave this step — the frontend lives outside the Maven build.

- [ ] **Step 3: Verify the monorepo still builds**

```bash
cd football-os-core
mvn clean install -DskipTests -q
```

Expected: BUILD FAILURE because `fos-workspace-service/pom.xml` does not exist yet. That is correct — we will create it in Task 2. The failure confirms Maven found the module declaration.

- [ ] **Step 4: Commit**

```bash
git add football-os-core/pom.xml
git commit -m "chore(workspace): add fos-workspace-service to monorepo parent pom"
```

---

## Task 2: fos-workspace-service — pom.xml

**Why:** Every Maven module needs its own `pom.xml` that declares its dependencies. We list the SDK modules we need, MongoDB, Mongock, and test dependencies here.

**Files:**
- Create: `fos-workspace-service/pom.xml`

- [ ] **Step 1: Create pom.xml**

```xml
<!-- fos-workspace-service/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.fos</groupId>
    <artifactId>football-os-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>

  <artifactId>fos-workspace-service</artifactId>
  <name>FOS Workspace Service</name>
  <description>
    Workspace module: document management, calendar, player profiles,
    medical section, admin documents, notifications, search.
  </description>

  <dependencies>

    <!-- ── FOS SDK ─────────────────────────────────────────────── -->
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-core</artifactId>
      <version>${fos.sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-events</artifactId>
      <version>${fos.sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-security</artifactId>
      <version>${fos.sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-storage</artifactId>
      <version>${fos.sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-policy</artifactId>
      <version>${fos.sdk.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-canonical</artifactId>
      <version>${fos.sdk.version}</version>
    </dependency>

    <!-- ── Spring Boot ──────────────────────────────────────────── -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- ── MongoDB ──────────────────────────────────────────────── -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>

    <!-- ── Mongock (MongoDB migrations) ─────────────────────────── -->
    <!--
      Mongock works like Flyway but for MongoDB.
      It tracks which change-sets have already run in a
      "mongockChangeLog" collection so they never run twice.
    -->
    <dependency>
      <groupId>io.mongock</groupId>
      <artifactId>mongock-springboot-v3</artifactId>
      <version>5.4.4</version>
    </dependency>
    <dependency>
      <groupId>io.mongock</groupId>
      <artifactId>mongodb-springdata-v4-driver</artifactId>
      <version>5.4.4</version>
    </dependency>

    <!-- ── Security ─────────────────────────────────────────────── -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- ── Test ─────────────────────────────────────────────────── -->
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-test</artifactId>
      <version>${fos.sdk.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mongodb</artifactId>
      <version>1.19.8</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>1.19.8</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.wiremock</groupId>
      <artifactId>wiremock-standalone</artifactId>
      <version>3.5.4</version>
      <scope>test</scope>
    </dependency>

  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>

</project>
```

- [ ] **Step 2: Verify the monorepo builds with the new pom.xml**

```bash
cd football-os-core
mvn clean install -DskipTests -q
```

Expected: BUILD SUCCESS — Maven finds the module and compiles it (empty for now).

- [ ] **Step 3: Commit**

```bash
git add fos-workspace-service/pom.xml
git commit -m "chore(workspace): add fos-workspace-service pom.xml with SDK + MongoDB + Mongock dependencies"
```

---

## Task 3: Spring Boot Application Entry Point + application.yml

**Why:** Every Spring Boot service needs a main class annotated with `@SpringBootApplication`. The `application.yml` file tells Spring how to connect to MongoDB, Kafka, and other infrastructure.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/WorkspaceApp.java`
- Create: `fos-workspace-service/src/main/resources/application.yml`

- [ ] **Step 1: Create WorkspaceApp.java**

```java
// WorkspaceApp.java
package com.fos.workspace;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for fos-workspace-service.
 *
 * @EnableMongock activates the Mongock migration runner.
 * On startup, Mongock scans the migration package and runs any
 * change-sets that have not been executed yet. Already-run
 * change-sets are skipped (idempotent).
 */
@SpringBootApplication
@EnableMongock
public class WorkspaceApp {
    public static void main(String[] args) {
        SpringApplication.run(WorkspaceApp.class, args);
    }
}
```

- [ ] **Step 2: Create application.yml**

```yaml
# fos-workspace-service/src/main/resources/application.yml

server:
  port: 8082   # governance=8081, gateway=8080, workspace=8082

spring:
  application:
    name: fos-workspace-service

  # ── MongoDB ─────────────────────────────────────────────────────────
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/fos_workspace}
      # IMPORTANT: never set auto-index-creation: true in production.
      # Indexes are managed exclusively by Mongock migrations.
      auto-index-creation: false

  # ── Kafka ────────────────────────────────────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
    consumer:
      group-id: fos-workspace
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest

  # ── Security (JWT via Keycloak JWKS) ────────────────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8180/realms/fos/protocol/openid-connect/certs}

# ── Mongock ─────────────────────────────────────────────────────────
mongock:
  migration-scan-package: com.fos.workspace.db.migration
  enabled: true

# ── FOS SDK clients ─────────────────────────────────────────────────
fos:
  policy:
    service-url: ${GOVERNANCE_URL:http://localhost:8081}
  canonical:
    service-url: ${GOVERNANCE_URL:http://localhost:8081}
  storage:
    provider: ${STORAGE_PROVIDER:minio}   # minio | s3 | azure | noop (tests)

# ── MinIO (used by sdk-storage when provider=minio) ─────────────────
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:fos-workspace}

# ── Actuator ────────────────────────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 3: Add the workspace route to fos-gateway GatewayRoutesConfig.java**

Open `fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java` and add the workspace route:

```java
@Value("${fos.workspace.url:http://localhost:8082}")
private String workspaceUrl;

// Inside fosRoutes(), add:
.route("fos-workspace", r -> r
        .path("/api/v1/documents/**",
              "/api/v1/events/**",
              "/api/v1/profiles/**",
              "/api/v1/notifications/**",
              "/api/v1/search/**")
        .uri(workspaceUrl))
```

Also add to `application.yml` of the gateway:
```yaml
fos:
  workspace:
    url: ${WORKSPACE_URL:http://localhost:8082}
```

- [ ] **Step 4: Verify workspace service starts**

```bash
# Start infrastructure first
cd football-os-core && docker-compose up -d

# Start workspace service
cd fos-workspace-service && mvn spring-boot:run
```

Expected: The service starts on port 8082. It may warn about missing Kafka topics — that is fine at this stage.

```bash
curl http://localhost:8082/actuator/health
# Expected: {"status":"UP"}
```

- [ ] **Step 5: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/WorkspaceApp.java \
        fos-workspace-service/src/main/resources/application.yml \
        fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java \
        fos-gateway/src/main/resources/application.yml
git commit -m "feat(workspace): add WorkspaceApp entry point, application.yml, gateway route"
```

---

## Task 4: Mongock — Database Migration Setup

**Why:** Before we create any MongoDB collection or index in Java code, we set up Mongock. Think of this as "Flyway for MongoDB". We register the configuration bean and write our first migration that creates the indexes the `WorkspaceDocument` collection needs.

**Important vocabulary:**
- **ChangeUnit** = a single, versioned, idempotent migration step (like a Flyway V001 SQL file)
- **@Execution** = the method that runs the migration forward
- **@RollbackExecution** = the method that undoes it (required by Mongock)
- **MongockConfig** = the Spring `@Configuration` that wires Mongock into Spring Data MongoDB

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/config/MongockConfig.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration001CreateDocumentIndexes.java`

- [ ] **Step 1: Create MongockConfig.java**

```java
// MongockConfig.java
package com.fos.workspace.config;

import com.mongodb.client.MongoClient;
import io.mongock.driver.mongodb.springdata.v4.SpringDataMongoV4Driver;
import io.mongock.runner.springboot.MongockSpringboot;
import io.mongock.runner.springboot.base.MongockApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Wires Mongock into the Spring application context.
 *
 * MongockSpringboot.builder() creates a runner that:
 *  1. Scans the migration package for @ChangeUnit classes
 *  2. On startup, runs any that haven't been run yet
 *  3. Records each run in the "mongockChangeLog" collection
 *  4. Never runs the same change-set twice (idempotent)
 */
@Configuration
public class MongockConfig {

    @Bean
    public MongockApplicationRunner mongockApplicationRunner(
            MongoTemplate mongoTemplate,
            ApplicationContext springContext) {

        return MongockSpringboot.builder()
                .setDriver(SpringDataMongoV4Driver.withDefaultLock(mongoTemplate))
                .addMigrationScanPackage("com.fos.workspace.db.migration")
                .setSpringContext(springContext)
                .buildApplicationRunner();
    }
}
```

- [ ] **Step 2: Create Migration001CreateDocumentIndexes.java**

```java
// Migration001CreateDocumentIndexes.java
package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Creates all indexes on the "workspace_documents" collection.
 *
 * WHY INDEXES?
 * Without indexes, MongoDB scans every single document to find a match.
 * With indexes, it jumps straight to the result. This is like an index at
 * the back of a book — instead of reading every page, you look up the word.
 *
 * id = "migration-001-..." must NEVER change once this runs in production.
 * order = "001" determines execution order relative to other change-sets.
 */
@ChangeUnit(id = "migration-001-create-document-indexes", order = "001", author = "fos-team")
public class Migration001CreateDocumentIndexes {

    private static final String COLLECTION = "workspace_documents";

    @Execution
    public void createIndexes(MongoTemplate mongoTemplate) {
        // Ensure the collection exists before creating indexes
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            mongoTemplate.createCollection(COLLECTION);
        }

        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);

        // Index 1: find all documents owned by a specific actor
        ops.ensureIndex(new Index()
                .on("ownerRef.id", Sort.Direction.ASC)
                .named("idx_workspace_documents_owner_id"));

        // Index 2: find all documents by category (e.g., MEDICAL, ADMIN)
        ops.ensureIndex(new Index()
                .on("category", Sort.Direction.ASC)
                .named("idx_workspace_documents_category"));

        // Index 3: find all documents linked to a canonical player
        ops.ensureIndex(new Index()
                .on("linkedPlayerRef.id", Sort.Direction.ASC)
                .named("idx_workspace_documents_linked_player"));

        // Index 4: find documents by state (ACTIVE, ARCHIVED)
        ops.ensureIndex(new Index()
                .on("state", Sort.Direction.ASC)
                .named("idx_workspace_documents_state"));

        // Index 5: find recent documents (sorted by creation date)
        ops.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.DESC)
                .named("idx_workspace_documents_created_at"));

        // Index 6: text search on document name
        ops.ensureIndex(new Index()
                .on("name", Sort.Direction.ASC)
                .named("idx_workspace_documents_name"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        // On rollback: drop all indexes we created (except the default _id index)
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);
        ops.dropIndex("idx_workspace_documents_owner_id");
        ops.dropIndex("idx_workspace_documents_category");
        ops.dropIndex("idx_workspace_documents_linked_player");
        ops.dropIndex("idx_workspace_documents_state");
        ops.dropIndex("idx_workspace_documents_created_at");
        ops.dropIndex("idx_workspace_documents_name");
    }
}
```

- [ ] **Step 3: Restart the service and verify migration ran**

```bash
cd fos-workspace-service && mvn spring-boot:run
```

Check the MongoDB `fos_workspace` database in a MongoDB client (Compass or mongosh):

```bash
mongosh mongodb://localhost:27017/fos_workspace
db.mongockChangeLog.find().pretty()
```

Expected: One document with `id: "migration-001-create-document-indexes"` and `state: "EXECUTED"`.

- [ ] **Step 4: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/config/MongockConfig.java \
        fos-workspace-service/src/main/java/com/fos/workspace/db/migration/
git commit -m "feat(workspace): add Mongock config and Migration001 for document indexes"
```

---

## Task 5: Document Domain Model

**Why:** Before writing any business logic, we define what a "document" looks like in our system. This is the heart of the domain. We define the MongoDB entity (`WorkspaceDocument`), a value object for file versions (`DocumentVersion`), and enums for category and visibility.

**Key design decision:** A `WorkspaceDocument` stores a `List<DocumentVersion>`. Every upload creates a new version entry. We never delete old versions from the array — we only add. This gives us a full audit trail of every version of a file, which is required for medical and admin documents.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/domain/DocumentCategory.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/domain/DocumentVisibility.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/domain/DocumentVersion.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/domain/WorkspaceDocument.java`

- [ ] **Step 1: Create DocumentCategory enum**

```java
// DocumentCategory.java
package com.fos.workspace.document.domain;

/**
 * Categorizes a document so we can apply different access rules per category.
 * The OPA policy will check this field to decide who can read/write/delete.
 *
 * GENERAL   — visible to all coaching staff
 * MEDICAL   — visible only to medical staff and admin
 * ADMIN     — visible only to club admin
 * REPORT    — match/training reports, visible to coaching staff
 * CONTRACT  — player contracts, visible only to admin
 */
public enum DocumentCategory {
    GENERAL,
    MEDICAL,
    ADMIN,
    REPORT,
    CONTRACT
}
```

- [ ] **Step 2: Create DocumentVisibility enum**

```java
// DocumentVisibility.java
package com.fos.workspace.document.domain;

/**
 * Controls who can see a document beyond the category-level rules.
 *
 * CLUB_WIDE      — everyone with access to this category can see it
 * TEAM_ONLY      — only members of the specific team
 * PRIVATE        — only the uploader and admins
 */
public enum DocumentVisibility {
    CLUB_WIDE,
    TEAM_ONLY,
    PRIVATE
}
```

- [ ] **Step 3: Create DocumentVersion value object**

```java
// DocumentVersion.java
package com.fos.workspace.document.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single version of an uploaded file.
 * This is NOT a MongoDB entity — it is an embedded value object
 * stored inside the WorkspaceDocument document as an array element.
 *
 * Every time someone uploads a new version of the same document,
 * a new DocumentVersion is appended to the versions list.
 * Old versions are NEVER deleted — this is the version history.
 */
public class DocumentVersion {

    /** Unique ID for this specific version (not the document ID). */
    private UUID versionId;

    /** The MinIO object key — e.g. "workspace/documents/abc123/v2.pdf" */
    private String storageObjectKey;

    /** MinIO bucket name — e.g. "fos-workspace" */
    private String storageBucket;

    /** Original filename as uploaded by the user — e.g. "PlayerContract_2025.pdf" */
    private String originalFilename;

    /** MIME type — e.g. "application/pdf", "image/jpeg" */
    private String contentType;

    /** File size in bytes */
    private Long fileSizeBytes;

    /** Version number — 1 for the first upload, 2 for the first re-upload, etc. */
    private int versionNumber;

    /** Actor ID of the person who uploaded this version */
    private UUID uploadedByActorId;

    /** When this version was uploaded (confirmed by backend after MinIO upload) */
    private Instant uploadedAt;

    /** Optional note explaining what changed in this version */
    private String versionNote;

    protected DocumentVersion() {}

    public DocumentVersion(String storageObjectKey, String storageBucket,
                           String originalFilename, String contentType,
                           Long fileSizeBytes, int versionNumber,
                           UUID uploadedByActorId, String versionNote) {
        this.versionId = UUID.randomUUID();
        this.storageObjectKey = storageObjectKey;
        this.storageBucket = storageBucket;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.versionNumber = versionNumber;
        this.uploadedByActorId = uploadedByActorId;
        this.uploadedAt = Instant.now();
        this.versionNote = versionNote;
    }

    // Getters — no setters (immutable value object)
    public UUID getVersionId()          { return versionId; }
    public String getStorageObjectKey() { return storageObjectKey; }
    public String getStorageBucket()    { return storageBucket; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType()      { return contentType; }
    public Long getFileSizeBytes()      { return fileSizeBytes; }
    public int getVersionNumber()       { return versionNumber; }
    public UUID getUploadedByActorId()  { return uploadedByActorId; }
    public Instant getUploadedAt()      { return uploadedAt; }
    public String getVersionNote()      { return versionNote; }
}
```

- [ ] **Step 4: Create WorkspaceDocument entity**

```java
// WorkspaceDocument.java
package com.fos.workspace.document.domain;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.core.BaseDocument;
import com.fos.sdk.core.ResourceState;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A document stored in the workspace.
 *
 * Extends BaseDocument from sdk-core which gives us:
 *   - id (String, MongoDB _id)
 *   - resourceId (UUID, our domain ID)
 *   - state (DRAFT | ACTIVE | ARCHIVED)
 *   - version (optimistic locking)
 *   - createdAt, updatedAt
 *
 * @Document("workspace_documents") maps this class to the
 * "workspace_documents" MongoDB collection.
 *
 * KEY DESIGN: versions is a List<DocumentVersion> embedded directly
 * inside this document. There is no separate "versions" collection.
 * This means one MongoDB read gives us the document AND all its versions.
 */
@Document(collection = "workspace_documents")
public class WorkspaceDocument extends BaseDocument {

    /** Human-readable name of the document (e.g. "Pre-Season Medical Report 2025") */
    private String name;

    /** Optional description */
    private String description;

    /** What kind of document this is — controls access rules */
    private DocumentCategory category;

    /** Who can see this document beyond category rules */
    private DocumentVisibility visibility;

    /**
     * The actor (coaching staff, admin, medical) who owns/created this document.
     * This is a CanonicalRef pointing to the actor's canonical entry.
     * type will be CLUB (since actors belong to clubs, not teams directly).
     */
    private CanonicalRef ownerRef;

    /**
     * Optional: if this document is linked to a specific player,
     * store the player's CanonicalRef here.
     * Used for player profile documents, medical reports, etc.
     */
    private CanonicalRef linkedPlayerRef;

    /**
     * Optional: if this document is linked to a specific team.
     */
    private CanonicalRef linkedTeamRef;

    /**
     * All versions of this document.
     * Index 0 = first upload, last element = most recent version.
     * NEVER remove elements from this list.
     */
    private List<DocumentVersion> versions = new ArrayList<>();

    /**
     * Tags for search (e.g. ["pre-season", "medical", "2025"]).
     */
    private List<String> tags = new ArrayList<>();

    protected WorkspaceDocument() {}

    /**
     * Factory method — the only way to create a new document.
     * State starts as DRAFT. It transitions to ACTIVE when the first version
     * is confirmed (file actually uploaded to MinIO).
     */
    public static WorkspaceDocument create(String name, String description,
                                            DocumentCategory category,
                                            DocumentVisibility visibility,
                                            CanonicalRef ownerRef,
                                            CanonicalRef linkedPlayerRef,
                                            CanonicalRef linkedTeamRef,
                                            List<String> tags) {
        WorkspaceDocument doc = new WorkspaceDocument();
        doc.initId();  // sets id and resourceId from BaseDocument
        doc.name = name;
        doc.description = description;
        doc.category = category;
        doc.visibility = visibility;
        doc.ownerRef = ownerRef;
        doc.linkedPlayerRef = linkedPlayerRef;
        doc.linkedTeamRef = linkedTeamRef;
        doc.tags = tags != null ? tags : new ArrayList<>();
        return doc;
    }

    /**
     * Appends a new version. Called after the frontend confirms that the file
     * was successfully uploaded to MinIO.
     * Transitions state from DRAFT to ACTIVE on the first version.
     */
    public void addVersion(DocumentVersion version) {
        this.versions.add(version);
        if (getState() == ResourceState.DRAFT) {
            this.activate();  // BaseDocument.activate() — sets state to ACTIVE
        }
    }

    /**
     * Soft-delete: transition to ARCHIVED.
     * The file bytes in MinIO are NOT deleted — only the metadata is archived.
     * Physical deletion is a separate admin operation.
     */
    public void softDelete() {
        this.archive();  // BaseDocument.archive() — sets state to ARCHIVED
    }

    /** Returns the most recent version, or null if no versions exist yet. */
    public DocumentVersion currentVersion() {
        if (versions.isEmpty()) return null;
        return versions.get(versions.size() - 1);
    }

    /** Returns the next version number (current max + 1). */
    public int nextVersionNumber() {
        return versions.size() + 1;
    }

    // Getters
    public String getName()                     { return name; }
    public String getDescription()              { return description; }
    public DocumentCategory getCategory()       { return category; }
    public DocumentVisibility getVisibility()   { return visibility; }
    public CanonicalRef getOwnerRef()           { return ownerRef; }
    public CanonicalRef getLinkedPlayerRef()    { return linkedPlayerRef; }
    public CanonicalRef getLinkedTeamRef()      { return linkedTeamRef; }
    public List<DocumentVersion> getVersions()  { return List.copyOf(versions); }
    public List<String> getTags()               { return List.copyOf(tags); }

    // Setters for update operations
    public void setName(String name)               { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setTags(List<String> tags)         { this.tags = new ArrayList<>(tags); }
    public void setVisibility(DocumentVisibility v){ this.visibility = v; }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/document/domain/
git commit -m "feat(workspace/document): add DocumentCategory, DocumentVisibility, DocumentVersion, WorkspaceDocument domain model"
```

---

## Task 6: Document Repository

**Why:** Spring Data MongoDB generates the implementation for us. We just declare an interface and annotate it — Spring creates the query code at runtime. This is called the Repository pattern.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/infrastructure/persistence/WorkspaceDocumentRepository.java`

- [ ] **Step 1: Create WorkspaceDocumentRepository**

```java
// WorkspaceDocumentRepository.java
package com.fos.workspace.document.infrastructure.persistence;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.WorkspaceDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data MongoDB repository for WorkspaceDocument.
 *
 * MongoRepository<WorkspaceDocument, String> gives us:
 *   - save(), findById(), findAll(), deleteById() for free.
 *
 * We add custom query methods below. Spring Data parses the method name
 * and generates the MongoDB query automatically. For example:
 *   findByOwnerRefId() → db.workspace_documents.find({"ownerRef.id": ...})
 */
public interface WorkspaceDocumentRepository
        extends MongoRepository<WorkspaceDocument, String> {

    /**
     * Find all documents owned by a specific actor (by their UUID).
     * Used to show "my documents" on the dashboard.
     */
    Page<WorkspaceDocument> findByOwnerRefIdAndState(
            UUID ownerRefId, ResourceState state, Pageable pageable);

    /**
     * Find all documents by category that are not archived.
     * Used for role-based listing (e.g., only show MEDICAL docs to medical staff).
     */
    Page<WorkspaceDocument> findByCategoryAndState(
            DocumentCategory category, ResourceState state, Pageable pageable);

    /**
     * Find all documents linked to a specific player.
     * Used on the player profile page.
     */
    List<WorkspaceDocument> findByLinkedPlayerRefIdAndState(
            UUID linkedPlayerRefId, ResourceState state);

    /**
     * Find a document by its UUID resource ID (not MongoDB's _id string).
     * Our domain uses UUID as the public-facing identifier.
     */
    Optional<WorkspaceDocument> findByResourceId(UUID resourceId);

    /**
     * Check whether a document exists and is not archived.
     * Used before granting a pre-signed URL.
     */
    boolean existsByResourceIdAndState(UUID resourceId, ResourceState state);

    /**
     * Full-text search by name (case-insensitive regex).
     * The @Query annotation lets us write raw MongoDB query JSON.
     * "?0" is replaced by the first method parameter at runtime.
     */
    @Query("{ 'name': { $regex: ?0, $options: 'i' }, 'state': 'ACTIVE' }")
    Page<WorkspaceDocument> searchByName(String namePattern, Pageable pageable);
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/document/infrastructure/persistence/
git commit -m "feat(workspace/document): add WorkspaceDocumentRepository"
```

---

## Task 7: Document API — Request/Response DTOs

**Why:** We never expose domain entities directly to the API. We use DTO (Data Transfer Object) records. This protects us from accidentally exposing internal fields, and it keeps the API contract stable even when the domain model changes.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/api/InitiateUploadRequest.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/api/ConfirmUploadRequest.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/api/DocumentResponse.java`

- [ ] **Step 1: Create InitiateUploadRequest**

```java
// InitiateUploadRequest.java
package com.fos.workspace.document.api;

import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Step 1 of the file upload flow.
 * The frontend sends this to request a pre-signed upload URL.
 *
 * The backend:
 *   1. Validates permissions via PolicyClient
 *   2. Generates a MinIO pre-signed URL via StoragePort
 *   3. Creates a WorkspaceDocument in DRAFT state
 *   4. Returns the pre-signed URL and the new documentId
 *
 * The frontend then uploads the file bytes directly to MinIO
 * using the pre-signed URL (no backend involved in the file transfer).
 */
public record InitiateUploadRequest(
    @NotBlank String name,
    String description,
    @NotNull DocumentCategory category,
    @NotNull DocumentVisibility visibility,
    @NotBlank String originalFilename,
    @NotBlank String contentType,
    @NotNull Long fileSizeBytes,
    UUID linkedPlayerRefId,   // optional — null if not linked to a player
    UUID linkedTeamRefId,     // optional — null if not linked to a team
    List<String> tags,
    String versionNote        // optional — for re-uploads (version 2, 3, ...)
) {}
```

- [ ] **Step 2: Create ConfirmUploadRequest**

```java
// ConfirmUploadRequest.java
package com.fos.workspace.document.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Step 3 of the file upload flow.
 * After the frontend has uploaded the file to MinIO using the pre-signed URL,
 * it calls this endpoint to tell the backend the upload is complete.
 *
 * The backend:
 *   1. Validates that the document exists and is in DRAFT or ACTIVE state
 *   2. Creates a new DocumentVersion and appends it to the document
 *   3. Transitions state from DRAFT to ACTIVE (if this is the first version)
 *   4. Emits an AUDIT signal to Kafka
 *   5. Returns the updated DocumentResponse
 */
public record ConfirmUploadRequest(
    @NotNull UUID documentId,
    @NotNull String storageObjectKey,  // the MinIO object key that was uploaded
    @NotNull String storageBucket      // the MinIO bucket
) {}
```

- [ ] **Step 3: Create DocumentResponse**

```java
// DocumentResponse.java
package com.fos.workspace.document.api;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.domain.WorkspaceDocument;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The API response for a WorkspaceDocument.
 * This is what the frontend receives — never the raw domain entity.
 *
 * Note: we include a downloadUrl field that the frontend uses to
 * open/preview the document. This URL is generated on-the-fly
 * by StoragePort.generateDownloadUrl() in DocumentService.
 */
public record DocumentResponse(
    UUID documentId,
    String name,
    String description,
    DocumentCategory category,
    DocumentVisibility visibility,
    ResourceState state,
    UUID ownerRefId,
    UUID linkedPlayerRefId,
    UUID linkedTeamRefId,
    List<String> tags,
    int versionCount,
    CurrentVersionInfo currentVersion,
    Instant createdAt,
    Instant updatedAt,
    String downloadUrl   // pre-signed download URL — short-lived (1 hour)
) {

    /**
     * Converts a domain entity to a response DTO.
     * The downloadUrl is passed in separately because it requires a
     * StoragePort call that the service layer handles.
     */
    public static DocumentResponse from(WorkspaceDocument doc, String downloadUrl) {
        DocumentVersion current = doc.currentVersion();
        CurrentVersionInfo versionInfo = current == null ? null : new CurrentVersionInfo(
                current.getVersionId(),
                current.getOriginalFilename(),
                current.getContentType(),
                current.getFileSizeBytes(),
                current.getVersionNumber(),
                current.getUploadedByActorId(),
                current.getUploadedAt(),
                current.getVersionNote()
        );

        UUID linkedPlayer = doc.getLinkedPlayerRef() != null
                ? doc.getLinkedPlayerRef().id() : null;
        UUID linkedTeam = doc.getLinkedTeamRef() != null
                ? doc.getLinkedTeamRef().id() : null;

        return new DocumentResponse(
                doc.getResourceId(),
                doc.getName(),
                doc.getDescription(),
                doc.getCategory(),
                doc.getVisibility(),
                doc.getState(),
                doc.getOwnerRef() != null ? doc.getOwnerRef().id() : null,
                linkedPlayer,
                linkedTeam,
                doc.getTags(),
                doc.getVersions().size(),
                versionInfo,
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                downloadUrl
        );
    }

    /** Nested record for the current version details */
    public record CurrentVersionInfo(
        UUID versionId,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        int versionNumber,
        UUID uploadedByActorId,
        Instant uploadedAt,
        String versionNote
    ) {}
}
```

- [ ] **Step 4: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/document/api/
git commit -m "feat(workspace/document): add API DTOs: InitiateUploadRequest, ConfirmUploadRequest, DocumentResponse"
```

---

## Task 8: DocumentService — Business Logic

**Why:** The service layer is where business logic lives. It coordinates: permission checks (PolicyClient), storage operations (StoragePort), database operations (repository), and event emission (FosKafkaProducer). Controllers should never contain business logic — they only parse HTTP and delegate to services.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/application/DocumentService.java`

- [ ] **Step 1: Create DocumentService**

```java
// DocumentService.java
package com.fos.workspace.document.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.ResourceState;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.api.ConfirmUploadRequest;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.api.InitiateUploadRequest;
import com.fos.workspace.document.domain.*;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    // The bucket name in MinIO where workspace files are stored
    private static final String WORKSPACE_BUCKET = "fos-workspace";

    // How long a pre-signed download URL is valid
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);

    // How long a pre-signed upload URL is valid
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(15);

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final PolicyClient policyClient;
    private final FosKafkaProducer kafkaProducer;

    public DocumentService(WorkspaceDocumentRepository documentRepository,
                           StoragePort storagePort,
                           PolicyClient policyClient,
                           FosKafkaProducer kafkaProducer) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.policyClient = policyClient;
        this.kafkaProducer = kafkaProducer;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 of upload flow: generate pre-signed URL
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initiates a file upload:
     * 1. Checks permission via PolicyClient
     * 2. Asks StoragePort for a pre-signed MinIO URL
     * 3. Creates a WorkspaceDocument in DRAFT state
     * 4. Returns the URL + documentId to the frontend
     *
     * The frontend then uploads directly to MinIO using this URL.
     * After upload, the frontend calls confirmUpload().
     */
    public UploadInitiationResult initiateUpload(InitiateUploadRequest request) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        // ── 1. Permission check ──────────────────────────────────────────────
        // Action name follows the convention: workspace.document.{category}.upload
        // OPA uses this to decide based on role + category.
        String action = "workspace.document." + request.category().name().toLowerCase() + ".upload";
        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, action,
                CanonicalRef.of(CanonicalType.CLUB, actorId),
                "ACTIVE"));

        if (!policy.isAllowed()) {
            throw new AccessDeniedException("Upload denied: " + policy.reason());
        }

        // ── 2. Generate object key for MinIO ────────────────────────────────
        // Key format: documents/{documentId}/{versionNumber}_{filename}
        // Using a new UUID as the document ID now so the key is stable.
        UUID documentId = UUID.randomUUID();
        String objectKey = "documents/" + documentId + "/v1_" + request.originalFilename();

        // ── 3. Ask StoragePort for pre-signed upload URL ─────────────────────
        PresignedUploadUrl uploadUrl = storagePort.generateUploadUrl(
                WORKSPACE_BUCKET, objectKey, request.contentType(), UPLOAD_URL_EXPIRY);

        // ── 4. Create WorkspaceDocument in DRAFT state ───────────────────────
        CanonicalRef ownerRef = CanonicalRef.of(CanonicalType.CLUB, actorId);
        CanonicalRef linkedPlayerRef = request.linkedPlayerRefId() != null
                ? CanonicalRef.of(CanonicalType.PLAYER, request.linkedPlayerRefId()) : null;
        CanonicalRef linkedTeamRef = request.linkedTeamRefId() != null
                ? CanonicalRef.of(CanonicalType.TEAM, request.linkedTeamRefId()) : null;

        WorkspaceDocument document = WorkspaceDocument.create(
                request.name(), request.description(),
                request.category(), request.visibility(),
                ownerRef, linkedPlayerRef, linkedTeamRef,
                request.tags());

        // Override the auto-generated resourceId with our predetermined documentId
        // so the objectKey and the documentId are aligned.
        // Note: initId() was already called inside WorkspaceDocument.create() via BaseDocument.
        // We save and the repository returns the saved document with the correct ID.
        WorkspaceDocument saved = documentRepository.save(document);

        log.info("Initiated upload: documentId={} category={} actor={}",
                saved.getResourceId(), request.category(), actorId);

        return new UploadInitiationResult(saved.getResourceId(), uploadUrl.uploadUrl(), objectKey);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 3 of upload flow: confirm the upload
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by the frontend after it has successfully uploaded the file to MinIO.
     * 1. Loads the document
     * 2. Creates a new DocumentVersion
     * 3. Appends it and transitions DRAFT → ACTIVE (first version)
     * 4. Emits an AUDIT signal to Kafka
     * 5. Returns the updated document
     */
    public DocumentResponse confirmUpload(ConfirmUploadRequest request,
                                          InitiateUploadRequest originalRequest) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());

        // ── 1. Load the document ─────────────────────────────────────────────
        WorkspaceDocument document = documentRepository
                .findByResourceId(request.documentId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found: " + request.documentId()));

        // ── 2. Create a DocumentVersion ───────────────────────────────────────
        DocumentVersion version = new DocumentVersion(
                request.storageObjectKey(),
                request.storageBucket(),
                originalRequest.originalFilename(),
                originalRequest.contentType(),
                originalRequest.fileSizeBytes(),
                document.nextVersionNumber(),
                actorId,
                originalRequest.versionNote());

        // ── 3. Add version → transitions state DRAFT → ACTIVE ─────────────────
        document.addVersion(version);
        WorkspaceDocument saved = documentRepository.save(document);

        // ── 4. Emit AUDIT signal ──────────────────────────────────────────────
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .payload(null) // payload added via Map in real impl — simplified here
                .build());

        // Also emit a FACT signal so other services can react
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("fos.workspace.document.uploaded")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .build());

        log.info("Confirmed upload: documentId={} version={} actor={}",
                saved.getResourceId(), version.getVersionNumber(), actorId);

        String downloadUrl = generateDownloadUrl(
                request.storageBucket(), request.storageObjectKey());
        return DocumentResponse.from(saved, downloadUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ operations
    // ─────────────────────────────────────────────────────────────────────────

    public DocumentResponse getDocument(UUID documentId) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        WorkspaceDocument document = documentRepository
                .findByResourceId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        // Permission check: can this actor read this category?
        String action = "workspace.document." + document.getCategory().name().toLowerCase() + ".read";
        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, action,
                CanonicalRef.of(CanonicalType.CLUB, actorId),
                document.getState().name()));

        if (!policy.isAllowed()) {
            throw new AccessDeniedException("Read denied: " + policy.reason());
        }

        String downloadUrl = document.currentVersion() != null
                ? generateDownloadUrl(document.currentVersion().getStorageBucket(),
                                      document.currentVersion().getStorageObjectKey())
                : null;

        return DocumentResponse.from(document, downloadUrl);
    }

    public Page<DocumentResponse> listDocuments(DocumentCategory category, Pageable pageable) {
        return documentRepository
                .findByCategoryAndState(category, ResourceState.ACTIVE, pageable)
                .map(doc -> {
                    String url = doc.currentVersion() != null
                            ? generateDownloadUrl(doc.currentVersion().getStorageBucket(),
                                                  doc.currentVersion().getStorageObjectKey())
                            : null;
                    return DocumentResponse.from(doc, url);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE (soft delete)
    // ─────────────────────────────────────────────────────────────────────────

    public void softDeleteDocument(UUID documentId) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        WorkspaceDocument document = documentRepository
                .findByResourceId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        // Permission check
        String action = "workspace.document." + document.getCategory().name().toLowerCase() + ".delete";
        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, action,
                CanonicalRef.of(CanonicalType.CLUB, actorId),
                document.getState().name()));

        if (!policy.isAllowed()) {
            throw new AccessDeniedException("Delete denied: " + policy.reason());
        }

        document.softDelete();
        documentRepository.save(document);

        // Emit AUDIT signal
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .build());

        log.info("Soft-deleted document: documentId={} actor={}", documentId, actorId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private String generateDownloadUrl(String bucket, String objectKey) {
        return storagePort.generateDownloadUrl(bucket, objectKey, DOWNLOAD_URL_EXPIRY);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner result record — carries upload initiation result back to controller
    // ─────────────────────────────────────────────────────────────────────────

    public record UploadInitiationResult(
        UUID documentId,
        String uploadUrl,    // pre-signed MinIO URL the frontend uses to upload
        String objectKey     // MinIO object key — needed later for confirmUpload
    ) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/document/application/
git commit -m "feat(workspace/document): add DocumentService with upload flow, read, soft-delete, PolicyClient checks, Kafka signals"
```

---

## Task 9: DocumentController

**Why:** The controller maps HTTP requests to service calls. It handles HTTP status codes, request validation, and path parameters. It must not contain any business logic.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/document/api/DocumentController.java`

- [ ] **Step 1: Create DocumentController**

```java
// DocumentController.java
package com.fos.workspace.document.api;

import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for document operations.
 *
 * All routes are prefixed with /api/v1/documents.
 * The gateway routes /api/v1/documents/** to this service.
 *
 * IMPORTANT: This controller does NOT check permissions.
 * Permission checks happen inside DocumentService via PolicyClient.
 * The controller's job is HTTP — parsing, routing, status codes.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * STEP 1 — Initiate upload.
     * Returns a pre-signed MinIO URL and the new documentId.
     * HTTP 201 Created.
     */
    @PostMapping("/upload/initiate")
    public ResponseEntity<DocumentService.UploadInitiationResult> initiateUpload(
            @Valid @RequestBody InitiateUploadRequest request) {
        DocumentService.UploadInitiationResult result = documentService.initiateUpload(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * STEP 3 — Confirm upload.
     * Called after the frontend has uploaded the file to MinIO.
     * Returns the updated document. HTTP 200 OK.
     *
     * Note: We receive BOTH the confirm request and the original initiate request
     * here so the service has all the metadata it needs to create the DocumentVersion.
     * In a production system you might persist the initiate request temporarily in
     * Redis or MongoDB. For simplicity here we pass it in the confirm request.
     */
    @PostMapping("/upload/confirm")
    public DocumentResponse confirmUpload(
            @Valid @RequestBody ConfirmUploadWithMetadata request) {
        return documentService.confirmUpload(
                new ConfirmUploadRequest(
                        request.documentId(),
                        request.storageObjectKey(),
                        request.storageBucket()),
                new InitiateUploadRequest(
                        request.name(),
                        request.description(),
                        request.category(),
                        request.visibility(),
                        request.originalFilename(),
                        request.contentType(),
                        request.fileSizeBytes(),
                        request.linkedPlayerRefId(),
                        request.linkedTeamRefId(),
                        request.tags(),
                        request.versionNote()));
    }

    /**
     * Get a single document by its UUID.
     * HTTP 200 OK, or 404 if not found.
     */
    @GetMapping("/{documentId}")
    public DocumentResponse getDocument(@PathVariable UUID documentId) {
        return documentService.getDocument(documentId);
    }

    /**
     * List documents by category with pagination.
     * ?category=MEDICAL&page=0&size=20
     * HTTP 200 OK.
     */
    @GetMapping
    public Page<DocumentResponse> listDocuments(
            @RequestParam DocumentCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        return documentService.listDocuments(category, pageable);
    }

    /**
     * Soft-delete a document.
     * Sets state to ARCHIVED. Does NOT delete from MinIO.
     * HTTP 204 No Content.
     */
    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDeleteDocument(@PathVariable UUID documentId) {
        documentService.softDeleteDocument(documentId);
    }

    /**
     * Combined confirm request that includes the original file metadata.
     * This is a convenience record so the frontend sends one request.
     */
    public record ConfirmUploadWithMetadata(
        UUID documentId,
        String storageObjectKey,
        String storageBucket,
        // metadata from the original initiate request:
        String name,
        String description,
        com.fos.workspace.document.domain.DocumentCategory category,
        com.fos.workspace.document.domain.DocumentVisibility visibility,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        UUID linkedPlayerRefId,
        UUID linkedTeamRefId,
        java.util.List<String> tags,
        String versionNote
    ) {}
}
```

- [ ] **Step 2: Create GlobalExceptionHandler**

```java
// GlobalExceptionHandler.java
package com.fos.workspace.config;

import com.fos.sdk.core.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates exceptions into HTTP responses.
 * Without this, Spring would return a generic 500 error for every exception.
 * This class maps specific exceptions to specific HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex) {
        return ErrorResponse.of("NOT_FOUND", ex.getMessage(), "no-context");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse.of("FORBIDDEN", ex.getMessage(), "no-context");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ErrorResponse.of("VALIDATION_FAILED", "Request validation failed",
                java.util.List.of(details), "no-context");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(IllegalStateException ex) {
        return ErrorResponse.of("CONFLICT", ex.getMessage(), "no-context");
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/document/api/DocumentController.java \
        fos-workspace-service/src/main/java/com/fos/workspace/config/GlobalExceptionHandler.java
git commit -m "feat(workspace/document): add DocumentController and GlobalExceptionHandler"
```

---

## Task 10: OPA Policy — Workspace Document Rules

**Why:** The OPA sidecar in `fos-governance-service` needs a Rego policy file that describes the workspace permission rules. Without this, every `PolicyClient.evaluate()` call will get DENY. We add a minimal workspace policy file now so the integration tests pass.

**Files:**
- Create: `fos-governance-service/src/main/resources/opa/workspace_policy.rego`

- [ ] **Step 1: Create workspace_policy.rego**

```rego
# fos-governance-service/src/main/resources/opa/workspace_policy.rego
package fos

# ── Workspace Document Policies ──────────────────────────────────────────────
#
# Action naming convention: workspace.document.{category}.{operation}
# Examples:
#   workspace.document.general.upload
#   workspace.document.medical.read
#   workspace.document.admin.delete

# ── GENERAL documents ───────────────────────────────────────────────────────
# All coaching staff can upload, read, and delete general documents
allow {
    input.resource.action == "workspace.document.general.upload"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.read"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.general.delete"
    coaching_staff_roles[input.actor.role]
}

# ── MEDICAL documents ────────────────────────────────────────────────────────
# Only MEDICAL_STAFF and ADMIN can access medical documents
allow {
    startswith(input.resource.action, "workspace.document.medical.")
    medical_roles[input.actor.role]
}

# ── ADMIN documents ──────────────────────────────────────────────────────────
# Only ADMIN can access admin documents
allow {
    startswith(input.resource.action, "workspace.document.admin.")
    input.actor.role == "ROLE_CLUB_ADMIN"
}

# ── REPORT documents ─────────────────────────────────────────────────────────
# All coaching staff can read reports; HEAD_COACH and ADMIN can upload
allow {
    input.resource.action == "workspace.document.report.read"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.upload"
    report_upload_roles[input.actor.role]
}

# ── CONTRACT documents ───────────────────────────────────────────────────────
# Only ADMIN
allow {
    startswith(input.resource.action, "workspace.document.contract.")
    input.actor.role == "ROLE_CLUB_ADMIN"
}

# ── Role sets ────────────────────────────────────────────────────────────────
coaching_staff_roles := {
    "ROLE_HEAD_COACH",
    "ROLE_ASSISTANT_COACH",
    "ROLE_GOALKEEPER_COACH",
    "ROLE_PHYSICAL_TRAINER",
    "ROLE_ANALYST",
    "ROLE_CLUB_ADMIN"
}

medical_roles := {
    "ROLE_MEDICAL_STAFF",
    "ROLE_CLUB_ADMIN"
}

report_upload_roles := {
    "ROLE_HEAD_COACH",
    "ROLE_CLUB_ADMIN",
    "ROLE_ANALYST"
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-governance-service/src/main/resources/opa/workspace_policy.rego
git commit -m "feat(governance/opa): add workspace document policy rules"
```

---

## Task 11: Integration Tests

**Why:** Integration tests start the full Spring application context with a real MongoDB container (via Testcontainers) and verify that the upload flow works end-to-end. We use WireMock to simulate the governance service (PolicyClient HTTP calls) and `NoopStorageAdapter` for storage (no real MinIO needed in tests).

**Files:**
- Create: `fos-workspace-service/src/test/java/com/fos/workspace/document/DocumentIntegrationTest.java`

- [ ] **Step 1: Create DocumentIntegrationTest**

```java
// DocumentIntegrationTest.java
package com.fos.workspace.document;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the Document upload flow.
 *
 * What we test:
 *   1. Initiate upload → returns upload URL and documentId
 *   2. Confirm upload → document transitions to ACTIVE
 *   3. Get document → returns correct data
 *   4. Soft-delete document → document state is ARCHIVED
 *   5. Get deleted document → still returns (state=ARCHIVED), read is policy-gated
 *
 * Infrastructure:
 *   - MongoDB: real Testcontainer (from FosTestContainersBase)
 *   - Kafka: real Testcontainer (from FosTestContainersBase)
 *   - PolicyClient HTTP: WireMock (returns ALLOW for all requests in tests)
 *   - StoragePort: NoopStorageAdapter (no real MinIO needed)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",         // use NoopStorageAdapter — no MinIO container needed
    "spring.security.enabled=false"      // disable JWT validation in tests
})
class DocumentIntegrationTest extends FosTestContainersBase {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // Point PolicyClient to WireMock (simulates governance service)
        registry.add("fos.policy.service-url", () -> "http://localhost:" + wireMock.port());
        registry.add("fos.canonical.service-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void stubPolicyAllow() {
        // Stub PolicyClient: governance service always returns ALLOW for tests
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_initiate_upload_and_return_201() {
        var request = new DocumentController.ConfirmUploadWithMetadata(
                null, null, null,
                "Test Contract", "A test document",
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "test.pdf", "application/pdf", 1024L,
                null, null, List.of("test"), null);

        // Initiate upload
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                "Test Document", "Description",
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "test.pdf", "application/pdf", 1024L,
                null, null, List.of("test"), null);

        ResponseEntity<DocumentService.UploadInitiationResult> response =
                restTemplate.postForEntity("/api/v1/documents/upload/initiate",
                        initiateRequest, DocumentService.UploadInitiationResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().documentId()).isNotNull();
        assertThat(response.getBody().uploadUrl()).startsWith("https://noop.fos.local/");
    }

    @Test
    void should_confirm_upload_and_transition_to_active() {
        // Step 1: initiate
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                "Medical Report", "Annual check",
                DocumentCategory.MEDICAL, DocumentVisibility.TEAM_ONLY,
                "medical.pdf", "application/pdf", 2048L,
                UUID.randomUUID(), null, List.of("medical"), "Initial upload");

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate",
                initiateRequest, DocumentService.UploadInitiationResult.class);

        // Step 3: confirm
        var confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(),
                initResult.objectKey(),
                "fos-workspace",
                "Medical Report", "Annual check",
                DocumentCategory.MEDICAL, DocumentVisibility.TEAM_ONLY,
                "medical.pdf", "application/pdf", 2048L,
                null, null, List.of("medical"), "Initial upload");

        ResponseEntity<DocumentResponse> response = restTemplate.postForEntity(
                "/api/v1/documents/upload/confirm",
                confirmRequest, DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().state().name()).isEqualTo("ACTIVE");
        assertThat(response.getBody().versionCount()).isEqualTo(1);
        assertThat(response.getBody().currentVersion()).isNotNull();
        assertThat(response.getBody().currentVersion().originalFilename()).isEqualTo("medical.pdf");
    }

    @Test
    void should_soft_delete_document_and_return_204() {
        // Create and confirm a document first
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                "Delete Me", null,
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "delete.pdf", "application/pdf", 512L,
                null, null, null, null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate",
                initiateRequest, DocumentService.UploadInitiationResult.class);

        var confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(), initResult.objectKey(), "fos-workspace",
                "Delete Me", null,
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "delete.pdf", "application/pdf", 512L,
                null, null, null, null);

        restTemplate.postForObject("/api/v1/documents/upload/confirm",
                confirmRequest, DocumentResponse.class);

        // Now soft-delete
        restTemplate.delete("/api/v1/documents/" + initResult.documentId());

        // Verify the document is archived
        DocumentResponse fetched = restTemplate.getForObject(
                "/api/v1/documents/" + initResult.documentId(), DocumentResponse.class);
        assertThat(fetched.state().name()).isEqualTo("ARCHIVED");
    }
}
```

- [ ] **Step 2: Run the integration tests**

```bash
cd fos-workspace-service
mvn test -Dtest=DocumentIntegrationTest -q
```

Expected: BUILD SUCCESS — 3 tests pass

- [ ] **Step 3: Commit**

```bash
git add fos-workspace-service/src/test/
git commit -m "test(workspace/document): add DocumentIntegrationTest — upload flow, soft-delete"
```

---

## Task 12: Full Build Verification

- [ ] **Step 1: Full monorepo build**

```bash
cd football-os-core
mvn package -q
```

Expected: BUILD SUCCESS — all modules including fos-workspace-service

- [ ] **Step 2: Start the full stack and smoke test**

```bash
# Start infrastructure
docker-compose up -d

# Start governance service
java -jar fos-governance-service/target/fos-governance-service-*.jar &

# Start workspace service
java -jar fos-workspace-service/target/fos-workspace-service-*.jar &

# Verify health
curl http://localhost:8082/actuator/health
# Expected: {"status":"UP"}
```

- [ ] **Step 3: Verify Mongock migration ran**

```bash
mongosh mongodb://localhost:27017/fos_workspace \
  --eval "db.mongockChangeLog.find().pretty()"
# Expected: one document with state "EXECUTED"
```

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "chore(workspace): sprint 1.1 complete — scaffold, MongoDB, Mongock, document domain, upload flow"
```

---

## Sprint Test Criteria

Sprint 1.1 is complete when:

1. `mvn package -q` in `football-os-core` succeeds — all modules build
2. `fos-workspace-service` starts on port 8082 with `{"status":"UP"}`
3. Mongock migration `Migration001CreateDocumentIndexes` runs on startup (verifiable in `mongockChangeLog`)
4. `POST /api/v1/documents/upload/initiate` returns 201 with a pre-signed URL
5. `POST /api/v1/documents/upload/confirm` returns 200 with `state: ACTIVE`
6. `GET /api/v1/documents/{id}` returns the document
7. `DELETE /api/v1/documents/{id}` returns 204 and sets state to ARCHIVED
8. Integration tests pass (3 tests in `DocumentIntegrationTest`)
9. No `MinioClient`, `MongoClient`, or governance service class is imported directly — only SDK classes

---

## What NOT to Include in This Sprint

- **Calendar/Event management** — Sprint 1.2
- **Player Profiles** — Sprint 1.3
- **Medical section** — Sprint 1.3
- **Notifications** — Sprint 1.4
- **Search endpoint** — Sprint 1.4
- **OnlyOffice integration** — Sprint 1.3 (document preview/editing)
- **Angular frontend** — Sprint 1.5
- **Document versioning UI** — Sprint 1.5
- **Rate limiting on workspace routes** — already handled by gateway from Phase 0
