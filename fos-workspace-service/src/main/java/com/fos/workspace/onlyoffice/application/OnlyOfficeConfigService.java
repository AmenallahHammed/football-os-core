package com.fos.workspace.onlyoffice.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.DocumentCategory;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OnlyOfficeConfigService {

    private static final Duration FILE_URL_EXPIRY = Duration.ofMinutes(30);
    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FALLBACK_CLUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FALLBACK_ROLE = "ROLE_CLUB_ADMIN";
    private static final Set<String> VIEWABLE_FILE_TYPES = Set.of("docx", "xlsx", "pptx", "pdf");
    private static final Set<String> EDITABLE_FILE_TYPES = Set.of("docx", "xlsx", "pptx");

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final PolicyClient policyClient;
    private final FosSecurityContext securityContext;
    private final boolean securityEnabled;
    private final String documentServerUrl;
    private final String jwtSecret;
    private final int tokenExpiryMinutes;
    private final int serverPort;

    public OnlyOfficeConfigService(WorkspaceDocumentRepository documentRepository,
                                   StoragePort storagePort,
                                   PolicyClient policyClient,
                                   FosSecurityContext securityContext,
                                   @Value("${fos.security.enabled:true}") boolean securityEnabled,
                                   @Value("${fos.onlyoffice.document-server-url}") String documentServerUrl,
                                   @Value("${fos.onlyoffice.jwt-secret}") String jwtSecret,
                                   @Value("${fos.onlyoffice.token-expiry-minutes:60}") int tokenExpiryMinutes,
                                   @Value("${server.port:8082}") int serverPort) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.policyClient = policyClient;
        this.securityContext = securityContext;
        this.securityEnabled = securityEnabled;
        this.documentServerUrl = documentServerUrl;
        this.jwtSecret = jwtSecret;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
        this.serverPort = serverPort;
    }

    public OnlyOfficeConfigResponse generateConfig(OnlyOfficeConfigRequest request) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();

        WorkspaceDocument document = documentRepository.findByResourceIdAndOwnerRefId(request.documentId(), clubId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + request.documentId()));

        if (document.currentVersion() == null) {
            throw new IllegalStateException("Document has no uploaded versions yet");
        }

        String fileType = resolveFileType(document.currentVersion().getContentType());
        validateModeSupport(request.mode(), fileType);

        String action = "edit".equals(request.mode())
                ? "workspace.document." + document.getCategory().name().toLowerCase() + ".edit"
                : "workspace.document." + document.getCategory().name().toLowerCase() + ".read";

        PolicyResult policy = policyClient.evaluate(PolicyRequest.withContext(
                actorId,
                role,
                action,
                resourceRef(document, clubId),
                document.getState().name(),
                buildTenantPolicyContext(clubId)));

        if (!policy.isAllowed()) {
            throw new AccessDeniedException("Document access denied: " + policy.reason());
        }

        String fileUrl = storagePort.generateDownloadUrl(
                document.currentVersion().getStorageBucket(),
                document.currentVersion().getStorageObjectKey(),
                FILE_URL_EXPIRY);

        String documentType = resolveDocumentType(fileType);
        String onlyOfficeKey = document.getResourceId() + "_v" + document.currentVersion().getVersionNumber();
        String callbackUrl = "http://localhost:" + serverPort + "/api/v1/onlyoffice/callback/" + document.getResourceId();

        var documentConfig = new OnlyOfficeConfigResponse.DocumentConfig(
                fileType,
                onlyOfficeKey,
                document.getName(),
                fileUrl);

        var editorConfig = new OnlyOfficeConfigResponse.EditorConfig(
                callbackUrl,
                "en",
                new OnlyOfficeConfigResponse.UserConfig(actorId.toString(), "Current User"),
                "edit".equals(request.mode()) ? "edit" : "view",
                new OnlyOfficeConfigResponse.CustomizationConfig(true, false));

        String token = signConfig(Map.of(
                "document", documentConfig,
                "editorConfig", editorConfig,
                "documentType", documentType));

        var config = new OnlyOfficeConfigResponse.OnlyOfficeConfig(documentConfig, editorConfig, documentType, token);
        return new OnlyOfficeConfigResponse(documentServerUrl, config, token);
    }

    private String signConfig(Map<String, Object> claims) {
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("OnlyOffice JWT secret must be at least 32 bytes");
        }

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date expiry = new Date(System.currentTimeMillis() + (long) tokenExpiryMinutes * 60_000);

        return Jwts.builder()
                .claims(claims)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    private String resolveFileType(String contentType) {
        if (contentType == null) {
            throw new IllegalArgumentException("Unsupported OnlyOffice content type: null");
        }
        if (contentType.contains("wordprocessingml") || contentType.contains("msword")) {
            return "docx";
        }
        if (contentType.contains("spreadsheetml") || contentType.contains("excel")) {
            return "xlsx";
        }
        if (contentType.contains("presentationml") || contentType.contains("powerpoint")) {
            return "pptx";
        }
        if (contentType.contains("pdf")) {
            return "pdf";
        }
        throw new IllegalArgumentException("Unsupported OnlyOffice content type: " + contentType);
    }

    private String resolveDocumentType(String fileType) {
        return switch (fileType) {
            case "xlsx" -> "cell";
            case "pptx" -> "slide";
            default -> "word";
        };
    }

    private void validateModeSupport(String mode, String fileType) {
        if (!"view".equals(mode) && !"edit".equals(mode)) {
            throw new IllegalArgumentException("OnlyOffice mode must be 'view' or 'edit'");
        }
        if (!VIEWABLE_FILE_TYPES.contains(fileType)) {
            throw new IllegalArgumentException("Unsupported OnlyOffice file type: " + fileType);
        }
        if ("edit".equals(mode) && !EDITABLE_FILE_TYPES.contains(fileType)) {
            throw new IllegalArgumentException("OnlyOffice edit mode is not supported for file type: " + fileType);
        }
    }

    private CanonicalRef resourceRef(WorkspaceDocument document, UUID clubId) {
        if (document.getLinkedPlayerRef() != null) {
            return document.getLinkedPlayerRef();
        }
        if (document.getLinkedTeamRef() != null) {
            return document.getLinkedTeamRef();
        }
        if (document.getOwnerRef() != null) {
            return document.getOwnerRef();
        }
        if (document.getCategory() == DocumentCategory.CONTRACT) {
            return CanonicalRef.club(clubId);
        }
        return CanonicalRef.club(clubId);
    }

    private UUID currentActorId() {
        return securityEnabled ? securityContext.getActorId() : FALLBACK_ACTOR_ID;
    }

    private UUID currentClubId() {
        if (!securityEnabled) {
            return FALLBACK_CLUB_ID;
        }
        String clubId = securityContext.clubId();
        if (clubId == null || clubId.isBlank()) {
            throw new AccessDeniedException("Missing club context in token");
        }
        try {
            return UUID.fromString(clubId);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid club context in token");
        }
    }

    private String currentActorRole() {
        return securityEnabled ? securityContext.getRole() : FALLBACK_ROLE;
    }

    private Map<String, Object> buildTenantPolicyContext(UUID clubId) {
        Map<String, Object> tenant = new HashMap<>();
        tenant.put("clubId", clubId.toString());

        Map<String, Object> context = new HashMap<>();
        context.put("tenant", tenant);
        return context;
    }
}
