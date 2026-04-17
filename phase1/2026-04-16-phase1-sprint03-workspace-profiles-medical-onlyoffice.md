# Phase 1 Sprint 1.3 — fos-workspace-service: Player Profiles + Medical + Admin Documents

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three new features are added to `fos-workspace-service`:
1. **Player Profiles** — a dedicated API that aggregates a player's documents, reports, and medical files in one response. Access is tab-based and role-gated (Coaching Staff see Documents + Reports; Medical Staff see Medical; Admin sees all).
2. **Medical Section** — Medical Staff can upload medical documents; only Admin can edit/delete them. A separate OPA rule enforces this asymmetry.
3. **Admin Documents** — Admin-only upload, edit, and delete. A configurable visibility setting controls whether other roles can see the document name (but never download it).
4. **OnlyOffice Integration** — Documents with MIME types `application/vnd.openxmlformats-officedocument.*` and `application/msword` can be opened for live collaborative editing via OnlyOffice Document Server. The backend generates a signed OnlyOffice JWT config that the frontend uses to launch the editor.
5. **Mongock Migration 003** — player profile index on the documents collection (already exists, but we add a compound index for the profile query).

**Architecture:**
Player profiles are NOT a separate MongoDB collection. The profile API is a read-only aggregation view: it queries `workspace_documents` filtered by `linkedPlayerRefId` and `category`, groups them, and returns a structured `PlayerProfileResponse`. This avoids data duplication. The `medical` and `admin` packages do not create new collections — they reuse `WorkspaceDocument` with specific `DocumentCategory` values (`MEDICAL`, `ADMIN`, `CONTRACT`). The OPA policies differentiate who can perform what operation per category. OnlyOffice runs as a separate Docker container; the backend only generates configuration tokens — it never proxies file bytes.

**Tech Stack:** Java 21, Spring Boot 3.3.x, MongoDB 7, Mongock, OnlyOffice JWT signing (JJWT library), `sdk-storage` (StoragePort), `sdk-policy` (PolicyClient), `sdk-canonical` (CanonicalRef, CanonicalResolver, PlayerDTO), JUnit 5, Testcontainers, WireMock

**Required Patterns This Sprint:**
- `[REQUIRED]` **Facade** — `PlayerProfileService` provides a single simplified API that hides the complexity of querying multiple document categories.
- `[REQUIRED]` **Proxy (Remote)** — `PolicyClient` for all per-tab permission checks.
- `[REQUIRED]` **Proxy (Caching)** — `CanonicalResolver` is used to fetch player identity data for the profile header.
- `[RECOMMENDED]` **Composite** — `PlayerProfileResponse` is a composite DTO that assembles sub-sections (documents, reports, medical) that are individually gated.

---

## File Map

```
fos-workspace-service/
└── src/
    ├── main/java/com/fos/workspace/
    │   ├── profile/
    │   │   ├── api/
    │   │   │   ├── PlayerProfileController.java                       CREATE
    │   │   │   └── PlayerProfileResponse.java                         CREATE
    │   │   └── application/
    │   │       └── PlayerProfileService.java                          CREATE
    │   ├── onlyoffice/
    │   │   ├── api/
    │   │   │   ├── OnlyOfficeController.java                          CREATE
    │   │   │   ├── OnlyOfficeConfigRequest.java                        CREATE
    │   │   │   └── OnlyOfficeConfigResponse.java                      CREATE
    │   │   └── application/
    │   │       └── OnlyOfficeConfigService.java                       CREATE
    │   └── db/migration/
    │       └── Migration003AddPlayerProfileIndex.java                 CREATE
    └── test/java/com/fos/workspace/
        ├── profile/
        │   └── PlayerProfileIntegrationTest.java                      CREATE
        └── onlyoffice/
            └── OnlyOfficeConfigTest.java                              CREATE

fos-governance-service/src/main/resources/opa/workspace_policy.rego   MODIFY (add medical/admin rules)
docker-compose.yml                                                      MODIFY (add OnlyOffice container)
```

---

## Task 1: Add OnlyOffice to docker-compose.yml

**Why:** OnlyOffice Document Server is a separate process that handles real-time collaborative editing. Our backend never touches file bytes for editing — it only tells the frontend how to connect to OnlyOffice. We add the Docker container now so local development has it available.

**Files:**
- Modify: `football-os-core/docker-compose.yml`

- [ ] **Step 1: Add OnlyOffice service**

Add to `docker-compose.yml` services block:

```yaml
  onlyoffice:
    image: onlyoffice/documentserver:7.5
    container_name: fos-onlyoffice
    ports:
      - "8090:80"
    environment:
      JWT_ENABLED: "true"
      JWT_SECRET: ${ONLYOFFICE_JWT_SECRET:-fos-onlyoffice-secret-key-change-in-production}
    volumes:
      - fos-onlyoffice-data:/var/www/onlyoffice/Data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/healthcheck"]
      interval: 30s
      timeout: 10s
      retries: 5
```

Also add the volume:
```yaml
volumes:
  fos-onlyoffice-data:
```

Add to `.env.example`:
```
ONLYOFFICE_JWT_SECRET=fos-onlyoffice-secret-key-change-in-production
ONLYOFFICE_URL=http://localhost:8090
```

- [ ] **Step 2: Add OnlyOffice config to workspace application.yml**

```yaml
fos:
  onlyoffice:
    document-server-url: ${ONLYOFFICE_URL:http://localhost:8090}
    jwt-secret: ${ONLYOFFICE_JWT_SECRET:fos-onlyoffice-secret-key-change-in-production}
    # How long an OnlyOffice editing session token is valid (minutes)
    token-expiry-minutes: 60
```

- [ ] **Step 3: Add JJWT dependency to workspace pom.xml**

```xml
<!-- For signing OnlyOffice JWT configuration tokens -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.5</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.5</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.5</version>
  <scope>runtime</scope>
</dependency>
```

- [ ] **Step 4: Commit**

```bash
git add football-os-core/docker-compose.yml fos-workspace-service/pom.xml \
        fos-workspace-service/src/main/resources/application.yml
git commit -m "chore(workspace): add OnlyOffice to docker-compose, JJWT dependency, config"
```

---

## Task 2: Mongock Migration 003 — Player Profile Compound Index

**Why:** The player profile query needs to efficiently find ALL documents linked to a player, across all categories, ordered by upload date. A compound index on `(linkedPlayerRef.id, category, state)` makes this query fast.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration003AddPlayerProfileIndex.java`

- [ ] **Step 1: Create Migration003**

```java
// Migration003AddPlayerProfileIndex.java
package com.fos.workspace.db.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.bson.Document;

@ChangeUnit(id = "migration-003-player-profile-compound-index", order = "003", author = "fos-team")
public class Migration003AddPlayerProfileIndex {

    private static final String COLLECTION = "workspace_documents";
    private static final String INDEX_NAME = "idx_workspace_documents_player_profile";

    @Execution
    public void addCompoundIndex(MongoTemplate mongoTemplate) {
        IndexOperations ops = mongoTemplate.indexOps(COLLECTION);

        // Compound index: linkedPlayerRef.id + category + state
        // This covers the exact query pattern used by PlayerProfileService:
        //   find({ linkedPlayerRef.id: X, category: Y, state: 'ACTIVE' })
        ops.ensureIndex(new CompoundIndexDefinition(
                new Document()
                        .append("linkedPlayerRef.id", 1)
                        .append("category", 1)
                        .append("state", 1))
                .named(INDEX_NAME));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.indexOps(COLLECTION).dropIndex(INDEX_NAME);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/db/migration/Migration003AddPlayerProfileIndex.java
git commit -m "feat(workspace): add Mongock Migration003 player profile compound index"
```

---

## Task 3: Player Profile — Response DTO and Service

**Why:** The player profile page shows different tabs depending on the viewer's role:
- Coaching Staff: sees the **Documents** tab (GENERAL + REPORT documents) and the **Profile Summary** (player identity from CanonicalResolver)
- Medical Staff: sees the **Medical** tab (MEDICAL documents only)
- Admin: sees all tabs

The `PlayerProfileService` (Facade pattern) performs all the permission checks and assembles the composite response. The controller only calls this one method.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/profile/api/PlayerProfileResponse.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/profile/application/PlayerProfileService.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/profile/api/PlayerProfileController.java`

- [ ] **Step 1: Create PlayerProfileResponse**

```java
// PlayerProfileResponse.java
package com.fos.workspace.profile.api;

import com.fos.workspace.document.api.DocumentResponse;

import java.util.List;
import java.util.UUID;

/**
 * Composite response for the player profile page.
 *
 * Each section is independently populated based on what the
 * current actor is allowed to see. A section is null if the
 * actor does not have permission to see it.
 *
 * For example:
 *   - Coaching Staff sees: playerInfo, documents, reports (medicalRecords = null)
 *   - Medical Staff sees: playerInfo, medicalRecords (documents = null, reports = null)
 *   - Admin sees: all sections
 */
public record PlayerProfileResponse(
    // ── Player identity (from CanonicalResolver) ──────────────────────
    UUID playerId,
    String playerName,
    String position,
    String nationality,
    String dateOfBirth,
    UUID currentTeamId,

    // ── Document sections (null = actor has no permission to see this tab) ──
    List<DocumentResponse> documents,      // GENERAL documents
    List<DocumentResponse> reports,        // REPORT documents
    List<DocumentResponse> medicalRecords, // MEDICAL documents
    List<DocumentResponse> adminDocuments, // ADMIN/CONTRACT documents

    // ── Stats for the tab headers ─────────────────────────────────────
    int documentCount,
    int reportCount,
    int medicalRecordCount,
    int adminDocumentCount
) {}
```

- [ ] **Step 2: Create PlayerProfileService**

```java
// PlayerProfileService.java
package com.fos.workspace.profile.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.CanonicalResolver;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.core.ResourceState;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.profile.api.PlayerProfileResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Facade that assembles the player profile page.
 *
 * Facade pattern: instead of the controller making 4-5 separate service calls,
 * it calls this single method which coordinates everything internally.
 *
 * This service:
 *   1. Fetches player identity from CanonicalResolver (cached)
 *   2. Checks which tabs the current actor can access via PolicyClient
 *   3. Queries each permitted document category from MongoDB
 *   4. Returns a composite PlayerProfileResponse
 */
@Service
public class PlayerProfileService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final PolicyClient policyClient;
    private final CanonicalResolver canonicalResolver;

    public PlayerProfileService(WorkspaceDocumentRepository documentRepository,
                                 StoragePort storagePort,
                                 PolicyClient policyClient,
                                 CanonicalResolver canonicalResolver) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.policyClient = policyClient;
        this.canonicalResolver = canonicalResolver;
    }

    public PlayerProfileResponse getProfile(UUID playerId) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        // ── 1. Get player identity from CanonicalResolver (cached) ──────────
        PlayerDTO player = canonicalResolver.getPlayer(playerId);

        // ── 2. Check which tabs this actor can access ────────────────────────
        boolean canSeeDocuments = canAccess(actorId, role, "workspace.profile.tab.documents");
        boolean canSeeReports   = canAccess(actorId, role, "workspace.profile.tab.reports");
        boolean canSeeMedical   = canAccess(actorId, role, "workspace.profile.tab.medical");
        boolean canSeeAdmin     = canAccess(actorId, role, "workspace.profile.tab.admin");

        // ── 3. Query each permitted category ─────────────────────────────────
        List<DocumentResponse> documents = canSeeDocuments
                ? fetchDocuments(playerId, DocumentCategory.GENERAL) : null;
        List<DocumentResponse> reports = canSeeReports
                ? fetchDocuments(playerId, DocumentCategory.REPORT) : null;
        List<DocumentResponse> medicalRecords = canSeeMedical
                ? fetchDocuments(playerId, DocumentCategory.MEDICAL) : null;
        List<DocumentResponse> adminDocs = canSeeAdmin
                ? mergeLists(
                    fetchDocuments(playerId, DocumentCategory.ADMIN),
                    fetchDocuments(playerId, DocumentCategory.CONTRACT))
                : null;

        // ── 4. Assemble the composite response ───────────────────────────────
        return new PlayerProfileResponse(
                player.id(),
                player.name(),
                player.position(),
                player.nationality(),
                player.dateOfBirth() != null ? player.dateOfBirth().toString() : null,
                player.currentTeamId(),
                documents,
                reports,
                medicalRecords,
                adminDocs,
                documents != null ? documents.size() : 0,
                reports != null ? reports.size() : 0,
                medicalRecords != null ? medicalRecords.size() : 0,
                adminDocs != null ? adminDocs.size() : 0
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean canAccess(UUID actorId, String role, String action) {
        PolicyResult result = policyClient.evaluate(PolicyRequest.of(
                actorId, role, action,
                CanonicalRef.of(CanonicalType.CLUB, actorId), "ACTIVE"));
        return result.isAllowed();
    }

    private List<DocumentResponse> fetchDocuments(UUID playerId, DocumentCategory category) {
        List<WorkspaceDocument> docs = documentRepository
                .findByLinkedPlayerRefIdAndState(playerId, ResourceState.ACTIVE);

        return docs.stream()
                .filter(d -> d.getCategory() == category)
                .map(doc -> {
                    String downloadUrl = doc.currentVersion() != null
                            ? storagePort.generateDownloadUrl(
                                doc.currentVersion().getStorageBucket(),
                                doc.currentVersion().getStorageObjectKey(),
                                DOWNLOAD_URL_EXPIRY)
                            : null;
                    return DocumentResponse.from(doc, downloadUrl);
                })
                .toList();
    }

    @SafeVarargs
    private <T> List<T> mergeLists(List<T>... lists) {
        return java.util.Arrays.stream(lists)
                .filter(l -> l != null)
                .flatMap(List::stream)
                .toList();
    }
}
```

- [ ] **Step 3: Create PlayerProfileController**

```java
// PlayerProfileController.java
package com.fos.workspace.profile.api;

import com.fos.workspace.profile.application.PlayerProfileService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
public class PlayerProfileController {

    private final PlayerProfileService playerProfileService;

    public PlayerProfileController(PlayerProfileService playerProfileService) {
        this.playerProfileService = playerProfileService;
    }

    /**
     * Returns the player's profile with all document sections the current
     * actor is permitted to see. Sections the actor cannot see are null.
     */
    @GetMapping("/players/{playerId}")
    public PlayerProfileResponse getPlayerProfile(@PathVariable UUID playerId) {
        return playerProfileService.getProfile(playerId);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/profile/
git commit -m "feat(workspace/profile): add PlayerProfileService (Facade), PlayerProfileController, PlayerProfileResponse"
```

---

## Task 4: OnlyOffice Configuration Service

**Why:** When the frontend wants to open a document for editing, it needs an OnlyOffice "config" object that includes a signed JWT. Our backend generates this config. It tells OnlyOffice: which file to load, who is editing, and what permissions they have. The frontend passes this config to the OnlyOffice JavaScript SDK to launch the editor.

**Files:**
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/api/OnlyOfficeConfigRequest.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/api/OnlyOfficeConfigResponse.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/application/OnlyOfficeConfigService.java`
- Create: `fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/api/OnlyOfficeController.java`

- [ ] **Step 1: Create OnlyOfficeConfigRequest**

```java
// OnlyOfficeConfigRequest.java
package com.fos.workspace.onlyoffice.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * The frontend requests an OnlyOffice config for a specific document.
 * mode = "view" (read-only preview) or "edit" (collaborative editing)
 */
public record OnlyOfficeConfigRequest(
    @NotNull UUID documentId,
    @NotNull String mode  // "view" or "edit"
) {}
```

- [ ] **Step 2: Create OnlyOfficeConfigResponse**

```java
// OnlyOfficeConfigResponse.java
package com.fos.workspace.onlyoffice.api;

/**
 * The config object the frontend passes to the OnlyOffice JavaScript SDK.
 *
 * The frontend code would look like:
 *   new DocsAPI.DocEditor("editor-container", response.config);
 *
 * The 'token' field is a signed JWT that OnlyOffice validates.
 * It prevents unauthorized access to the document server.
 */
public record OnlyOfficeConfigResponse(
    String documentServerUrl,  // e.g., "http://localhost:8090"
    OnlyOfficeConfig config,   // the full OnlyOffice configuration object
    String token               // JWT signed with ONLYOFFICE_JWT_SECRET
) {
    public record OnlyOfficeConfig(
        DocumentConfig document,
        EditorConfig editorConfig,
        String documentType,   // "word", "cell", "slide"
        String token
    ) {}

    public record DocumentConfig(
        String fileType,       // "docx", "xlsx", "pdf"
        String key,            // unique document version key (used by OnlyOffice for caching)
        String title,          // display name in editor
        String url             // pre-signed download URL the OnlyOffice server uses to fetch the file
    ) {}

    public record EditorConfig(
        String callbackUrl,    // OnlyOffice calls this when saving
        String lang,
        UserConfig user,
        String mode,           // "view" or "edit"
        CustomizationConfig customization
    ) {}

    public record UserConfig(String id, String name) {}

    public record CustomizationConfig(boolean autosave, boolean forcesave) {}
}
```

- [ ] **Step 3: Create OnlyOfficeConfigService**

```java
// OnlyOfficeConfigService.java
package com.fos.workspace.onlyoffice.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.onlyoffice.api.OnlyOfficeConfigRequest;
import com.fos.workspace.onlyoffice.api.OnlyOfficeConfigResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Generates OnlyOffice editor configuration tokens.
 *
 * Flow:
 *   1. Load the WorkspaceDocument
 *   2. Check if the actor can open it (edit or view)
 *   3. Generate a pre-signed MinIO URL for the file bytes
 *   4. Build the OnlyOffice config JSON
 *   5. Sign the config as a JWT (required by OnlyOffice when JWT_ENABLED=true)
 *   6. Return config + JWT to frontend
 *
 * IMPORTANT: Only DOCX, XLSX, PPTX files can be opened in OnlyOffice.
 * PDFs can be previewed (view mode) but not edited.
 */
@Service
public class OnlyOfficeConfigService {

    private static final Duration FILE_URL_EXPIRY = Duration.ofMinutes(30);

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final PolicyClient policyClient;

    @Value("${fos.onlyoffice.document-server-url}")
    private String documentServerUrl;

    @Value("${fos.onlyoffice.jwt-secret}")
    private String jwtSecret;

    @Value("${fos.onlyoffice.token-expiry-minutes:60}")
    private int tokenExpiryMinutes;

    @Value("${server.port:8082}")
    private int serverPort;

    public OnlyOfficeConfigService(WorkspaceDocumentRepository documentRepository,
                                    StoragePort storagePort,
                                    PolicyClient policyClient) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.policyClient = policyClient;
    }

    public OnlyOfficeConfigResponse generateConfig(OnlyOfficeConfigRequest request) {
        UUID actorId = UUID.fromString(FosSecurityContext.actorId());
        String role = FosSecurityContext.roles().stream().findFirst().orElse("");

        // ── 1. Load the document ─────────────────────────────────────────────
        WorkspaceDocument document = documentRepository
                .findByResourceId(request.documentId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found: " + request.documentId()));

        if (document.currentVersion() == null) {
            throw new IllegalStateException("Document has no uploaded versions yet");
        }

        // ── 2. Permission check ──────────────────────────────────────────────
        String action = "edit".equals(request.mode())
                ? "workspace.document." + document.getCategory().name().toLowerCase() + ".edit"
                : "workspace.document." + document.getCategory().name().toLowerCase() + ".read";

        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(
                actorId, role, action,
                CanonicalRef.of(CanonicalType.CLUB, actorId),
                document.getState().name()));

        if (!policy.isAllowed()) {
            throw new AccessDeniedException("Document access denied: " + policy.reason());
        }

        // ── 3. Generate pre-signed URL ───────────────────────────────────────
        String fileUrl = storagePort.generateDownloadUrl(
                document.currentVersion().getStorageBucket(),
                document.currentVersion().getStorageObjectKey(),
                FILE_URL_EXPIRY);

        // ── 4. Determine document type ───────────────────────────────────────
        String contentType = document.currentVersion().getContentType();
        String fileType = resolveFileType(contentType);
        String docType = resolveDocumentType(fileType);

        // ── 5. Build OnlyOffice config ───────────────────────────────────────
        // The "key" must change every time the file changes (OnlyOffice cache invalidation).
        // We use documentId + versionNumber as a stable key.
        String onlyOfficeKey = document.getResourceId() + "_v"
                + document.currentVersion().getVersionNumber();

        // Callback URL: OnlyOffice calls this when the user saves the document.
        // We will implement the callback handler in a later sprint.
        String callbackUrl = "http://localhost:" + serverPort
                + "/api/v1/onlyoffice/callback/" + document.getResourceId();

        var documentConfig = new OnlyOfficeConfigResponse.DocumentConfig(
                fileType, onlyOfficeKey, document.getName(), fileUrl);

        var userConfig = new OnlyOfficeConfigResponse.UserConfig(
                actorId.toString(), "Current User"); // name resolved in frontend

        var editorConfig = new OnlyOfficeConfigResponse.EditorConfig(
                callbackUrl, "en",
                userConfig,
                "edit".equals(request.mode()) ? "edit" : "view",
                new OnlyOfficeConfigResponse.CustomizationConfig(true, false));

        var config = new OnlyOfficeConfigResponse.OnlyOfficeConfig(
                documentConfig, editorConfig, docType, null);

        // ── 6. Sign the config as a JWT ──────────────────────────────────────
        String token = signConfig(Map.of(
                "document", documentConfig,
                "editorConfig", editorConfig,
                "documentType", docType));

        var signedConfig = new OnlyOfficeConfigResponse.OnlyOfficeConfig(
                documentConfig, editorConfig, docType, token);

        return new OnlyOfficeConfigResponse(documentServerUrl, signedConfig, token);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String signConfig(Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date expiry = new Date(System.currentTimeMillis() + (long) tokenExpiryMinutes * 60_000);

        return Jwts.builder()
                .claims(claims)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    private String resolveFileType(String contentType) {
        if (contentType == null) return "docx";
        if (contentType.contains("wordprocessingml") || contentType.contains("msword")) return "docx";
        if (contentType.contains("spreadsheetml") || contentType.contains("excel")) return "xlsx";
        if (contentType.contains("presentationml") || contentType.contains("powerpoint")) return "pptx";
        if (contentType.contains("pdf")) return "pdf";
        return "docx"; // safe default
    }

    private String resolveDocumentType(String fileType) {
        return switch (fileType) {
            case "xlsx", "csv" -> "cell";
            case "pptx" -> "slide";
            default -> "word";
        };
    }
}
```

- [ ] **Step 4: Create OnlyOfficeController**

```java
// OnlyOfficeController.java
package com.fos.workspace.onlyoffice.api;

import com.fos.workspace.onlyoffice.application.OnlyOfficeConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/onlyoffice")
public class OnlyOfficeController {

    private final OnlyOfficeConfigService configService;

    public OnlyOfficeController(OnlyOfficeConfigService configService) {
        this.configService = configService;
    }

    /**
     * Returns the OnlyOffice editor configuration for a document.
     * The frontend passes this config to the OnlyOffice JavaScript SDK.
     *
     * mode = "view" for read-only preview, "edit" for collaborative editing.
     */
    @PostMapping("/config")
    public OnlyOfficeConfigResponse getEditorConfig(
            @Valid @RequestBody OnlyOfficeConfigRequest request) {
        return configService.generateConfig(request);
    }

    /**
     * OnlyOffice Document Server calls this URL when saving.
     * For Sprint 1.3, we return 200 OK (acknowledge but don't process).
     * Full save handling (creating a new document version) is in Sprint 1.4.
     */
    @PostMapping("/callback/{documentId}")
    public String handleSaveCallback(@PathVariable String documentId,
                                      @RequestBody String callbackBody) {
        // OnlyOffice expects: {"error": 0} to acknowledge successful receipt
        return "{\"error\": 0}";
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-workspace-service/src/main/java/com/fos/workspace/onlyoffice/
git commit -m "feat(workspace/onlyoffice): add OnlyOfficeConfigService, OnlyOfficeController, JWT signing"
```

---

## Task 5: OPA Policy — Player Profile Tabs + Medical + Admin Rules

- [ ] **Step 1: Append to workspace_policy.rego**

```rego
# ── Player Profile Tab Policies ──────────────────────────────────────────────

# All coaching staff can see the Documents and Reports tabs
allow {
    input.resource.action == "workspace.profile.tab.documents"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.profile.tab.reports"
    coaching_staff_roles[input.actor.role]
}

# Only medical staff and admin can see the Medical tab
allow {
    input.resource.action == "workspace.profile.tab.medical"
    medical_roles[input.actor.role]
}

# Only admin can see the Admin tab
allow {
    input.resource.action == "workspace.profile.tab.admin"
    input.actor.role == "ROLE_CLUB_ADMIN"
}

# ── Document Edit (OnlyOffice) Policies ──────────────────────────────────────
# Editing follows same rules as upload — same role can edit
allow {
    input.resource.action == "workspace.document.general.edit"
    coaching_staff_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.report.edit"
    report_upload_roles[input.actor.role]
}

allow {
    input.resource.action == "workspace.document.medical.edit"
    # IMPORTANT: Medical Staff can UPLOAD medical docs but only ADMIN can EDIT/DELETE them
    input.actor.role == "ROLE_CLUB_ADMIN"
}

allow {
    input.resource.action == "workspace.document.admin.edit"
    input.actor.role == "ROLE_CLUB_ADMIN"
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-governance-service/src/main/resources/opa/workspace_policy.rego
git commit -m "feat(governance/opa): add player profile tab, medical edit, admin edit policy rules"
```

---

## Task 6: Integration Tests

**Files:**
- Create: `fos-workspace-service/src/test/java/com/fos/workspace/profile/PlayerProfileIntegrationTest.java`
- Create: `fos-workspace-service/src/test/java/com/fos/workspace/onlyoffice/OnlyOfficeConfigTest.java`

- [ ] **Step 1: Create PlayerProfileIntegrationTest**

```java
// PlayerProfileIntegrationTest.java
package com.fos.workspace.profile;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.profile.api.PlayerProfileResponse;
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
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",
    "spring.security.enabled=false"
})
class PlayerProfileIntegrationTest extends FosTestContainersBase {

    static WireMockServer wireMock;
    static final UUID TEST_PLAYER_ID = UUID.randomUUID();

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() { wireMock.stop(); }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("fos.policy.service-url",    () -> "http://localhost:" + wireMock.port());
        registry.add("fos.canonical.service-url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeEach
    void stubServices() {
        // Stub PolicyClient: always ALLOW
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));

        // Stub CanonicalResolver: return a test player
        wireMock.stubFor(get(urlMatching("/api/v1/players/.*"))
                .willReturn(okJson("""
                    {"id":"%s","name":"Test Player","position":"ST",
                     "nationality":"SA","dateOfBirth":"1998-04-15","currentTeamId":null}
                    """.formatted(TEST_PLAYER_ID))));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return_profile_with_documents_section() {
        // Create a GENERAL document linked to this player
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                "Training Plan", "Weekly plan",
                DocumentCategory.GENERAL, DocumentVisibility.TEAM_ONLY,
                "plan.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                2048L, TEST_PLAYER_ID, null, List.of(), null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate", initiateRequest,
                DocumentService.UploadInitiationResult.class);

        var confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(), initResult.objectKey(), "fos-workspace",
                "Training Plan", "Weekly plan",
                DocumentCategory.GENERAL, DocumentVisibility.TEAM_ONLY,
                "plan.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                2048L, TEST_PLAYER_ID, null, List.of(), null);

        restTemplate.postForObject("/api/v1/documents/upload/confirm",
                confirmRequest, DocumentResponse.class);

        // Now get the player profile
        ResponseEntity<PlayerProfileResponse> response = restTemplate.getForEntity(
                "/api/v1/profiles/players/" + TEST_PLAYER_ID, PlayerProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().playerName()).isEqualTo("Test Player");
        assertThat(response.getBody().documentCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void should_return_profile_with_medical_section_for_medical_staff() {
        // Create a MEDICAL document linked to the player
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                "Medical Clearance", "Pre-season clearance",
                DocumentCategory.MEDICAL, DocumentVisibility.PRIVATE,
                "clearance.pdf", "application/pdf",
                1024L, TEST_PLAYER_ID, null, List.of("medical"), null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate", initiateRequest,
                DocumentService.UploadInitiationResult.class);

        var confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(), initResult.objectKey(), "fos-workspace",
                "Medical Clearance", "Pre-season clearance",
                DocumentCategory.MEDICAL, DocumentVisibility.PRIVATE,
                "clearance.pdf", "application/pdf",
                1024L, TEST_PLAYER_ID, null, List.of("medical"), null);

        restTemplate.postForObject("/api/v1/documents/upload/confirm",
                confirmRequest, DocumentResponse.class);

        ResponseEntity<PlayerProfileResponse> response = restTemplate.getForEntity(
                "/api/v1/profiles/players/" + TEST_PLAYER_ID, PlayerProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().medicalRecordCount()).isGreaterThanOrEqualTo(1);
    }
}
```

- [ ] **Step 2: Create OnlyOfficeConfigTest**

```java
// OnlyOfficeConfigTest.java
package com.fos.workspace.onlyoffice;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.onlyoffice.api.OnlyOfficeConfigRequest;
import com.fos.workspace.onlyoffice.api.OnlyOfficeConfigResponse;
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
    "spring.security.enabled=false",
    "fos.onlyoffice.document-server-url=http://localhost:8090",
    "fos.onlyoffice.jwt-secret=test-secret-key-must-be-32-chars!!"
})
class OnlyOfficeConfigTest extends FosTestContainersBase {

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
    void should_generate_onlyoffice_config_with_token() {
        // Upload a DOCX document first
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                "Test DOCX", null,
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                4096L, null, null, List.of(), null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate", initiateRequest,
                DocumentService.UploadInitiationResult.class);

        var confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(), initResult.objectKey(), "fos-workspace",
                "Test DOCX", null,
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                4096L, null, null, List.of(), null);

        DocumentResponse confirmed = restTemplate.postForObject(
                "/api/v1/documents/upload/confirm", confirmRequest, DocumentResponse.class);

        // Request OnlyOffice config
        var configRequest = new OnlyOfficeConfigRequest(confirmed.documentId(), "view");

        ResponseEntity<OnlyOfficeConfigResponse> response = restTemplate.postForEntity(
                "/api/v1/onlyoffice/config", configRequest, OnlyOfficeConfigResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().documentServerUrl()).isEqualTo("http://localhost:8090");
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().config().document().fileType()).isEqualTo("docx");
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
cd fos-workspace-service
mvn test -q
```

Expected: BUILD SUCCESS — all tests pass (DocumentIntegrationTest + EventIntegrationTest + PlayerProfileIntegrationTest + OnlyOfficeConfigTest)

- [ ] **Step 4: Commit and final build**

```bash
git add fos-workspace-service/src/test/
git commit -m "test(workspace): add PlayerProfileIntegrationTest, OnlyOfficeConfigTest"

cd football-os-core
mvn package -q
git commit -m "chore(workspace): sprint 1.3 complete — player profiles, medical/admin docs, OnlyOffice integration"
```

---

## Sprint Test Criteria

Sprint 1.3 is complete when:

1. All workspace tests pass (DocumentIntegrationTest + EventIntegrationTest + PlayerProfileIntegrationTest + OnlyOfficeConfigTest)
2. `GET /api/v1/profiles/players/{playerId}` returns the profile with role-gated sections
3. `POST /api/v1/onlyoffice/config` returns a config object with a valid JWT token
4. Medical documents uploaded by Medical Staff cannot be edited by non-admin actors (OPA denies)
5. Admin documents are only visible to actors with `ROLE_CLUB_ADMIN`
6. Mongock Migration003 ran successfully
7. OnlyOffice container starts via `docker-compose up -d onlyoffice`
8. `POST /api/v1/onlyoffice/callback/{documentId}` returns `{"error": 0}`

---

## What NOT to Include in This Sprint

- **OnlyOffice save callback full implementation** (creating a new document version from OnlyOffice's save) — Sprint 1.4
- **Angular profile UI** — Sprint 1.5
- **Player search inside profiles** — Sprint 1.4
- **Notification when a medical document is uploaded** — Sprint 1.4
