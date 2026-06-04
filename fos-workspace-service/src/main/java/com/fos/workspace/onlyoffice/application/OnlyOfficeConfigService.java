package com.fos.workspace.onlyoffice.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.onlyoffice.api.OnlyOfficeConfigRequest;
import com.fos.workspace.onlyoffice.api.OnlyOfficeConfigResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OnlyOfficeConfigService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeConfigService.class);
    // Local no-auth development fallback values only.
    private static final UUID LOCAL_NOAUTH_FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LOCAL_NOAUTH_FALLBACK_CLUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String LOCAL_NOAUTH_FALLBACK_ROLE = "ROLE_CLUB_ADMIN";
    private static final Set<String> VIEWABLE_FILE_TYPES = Set.of("docx", "xlsx", "pptx", "pdf");
    private static final Set<String> EDITABLE_FILE_TYPES = Set.of("docx", "xlsx", "pptx");

    private final WorkspaceDocumentRepository documentRepository;
    private final PolicyClient policyClient;
    private final FosSecurityContext securityContext;
    private final boolean securityEnabled;
    private final String documentServerUrl;
    private final String backendPublicUrl;
    private final String callbackBaseUrl;
    private final String jwtSecret;
    private final int tokenExpiryMinutes;
    private final OnlyOfficeDownloadTokenService downloadTokenService;

    public OnlyOfficeConfigService(WorkspaceDocumentRepository documentRepository,
                                   PolicyClient policyClient,
                                   FosSecurityContext securityContext,
                                    @Value("${fos.security.enabled:true}") boolean securityEnabled,
                                    @Value("${fos.onlyoffice.document-server-url}") String documentServerUrl,
                                   @Value("${fos.onlyoffice.backend-public-url:${fos.onlyoffice.callback-base-url}}") String backendPublicUrl,
                                   @Value("${fos.onlyoffice.callback-base-url}") String callbackBaseUrl,
                                   @Value("${fos.onlyoffice.jwt-secret}") String jwtSecret,
                                   @Value("${fos.onlyoffice.token-expiry-minutes:60}") int tokenExpiryMinutes,
                                   OnlyOfficeDownloadTokenService downloadTokenService) {
        this.documentRepository = documentRepository;
        this.policyClient = policyClient;
        this.securityContext = securityContext;
        this.securityEnabled = securityEnabled;
        this.documentServerUrl = trimTrailingSlashes(documentServerUrl);
        this.backendPublicUrl = trimTrailingSlashes(backendPublicUrl);
        this.callbackBaseUrl = trimTrailingSlashes(callbackBaseUrl);
        this.jwtSecret = jwtSecret;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
        this.downloadTokenService = downloadTokenService;
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

        String documentType = resolveDocumentType(fileType);
        String onlyOfficeKey = document.getResourceId() + "_v" + document.currentVersion().getVersionNumber();
        String downloadToken = downloadTokenService.signDownloadToken(
                document.getResourceId(),
                document.currentVersion().getVersionNumber());
        String fileUrl = backendPublicUrl + "/api/v1/onlyoffice/download/" + onlyOfficeKey + "?token=" + downloadToken;
        String callbackUrl = callbackBaseUrl + "/api/v1/onlyoffice/callback/" + document.getResourceId();

        var documentConfig = new OnlyOfficeConfigResponse.DocumentConfig(
                fileType,
                onlyOfficeKey,
                document.currentVersion().getOriginalFilename(),
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

        logConfig(document.getResourceId(), request.mode(), documentConfig, editorConfig);

        var config = new OnlyOfficeConfigResponse.OnlyOfficeConfig(documentConfig, editorConfig, documentType, token);
        return new OnlyOfficeConfigResponse(documentServerUrl, config, token);
    }

    private void logConfig(UUID documentId,
                           String mode,
                           OnlyOfficeConfigResponse.DocumentConfig documentConfig,
                           OnlyOfficeConfigResponse.EditorConfig editorConfig) {
        log.info(
                "OnlyOffice config generated: documentId={} mode={} documentUrl={} callbackUrl={} documentKey={} fileType={}",
                documentId,
                mode,
                sanitizeUrlForLogs(documentConfig.url()),
                editorConfig.callbackUrl(),
                documentConfig.key(),
                documentConfig.fileType());
        
        // Enhanced diagnostic logging for troubleshooting
        log.debug(
                "OnlyOffice config debug: backendPublicUrl={} documentServerUrl={} callbackBaseUrl={} tokenExpiryMinutes={}",
                backendPublicUrl,
                documentServerUrl,
                callbackBaseUrl,
                tokenExpiryMinutes);
        
        // Full URL with token (only in debug to avoid accidentally logging secrets)
        if (log.isDebugEnabled()) {
            log.debug("OnlyOffice download URL with token: {} (full URL for testing)", documentConfig.url());
        }
    }

    private String sanitizeUrlForLogs(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
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

    private String trimTrailingSlashes(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OnlyOffice URL configuration must not be blank");
        }
        return value.replaceAll("/+$", "");
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
        return securityEnabled ? securityContext.getActorId() : LOCAL_NOAUTH_FALLBACK_ACTOR_ID;
    }

    private UUID currentClubId() {
        if (!securityEnabled) {
            return LOCAL_NOAUTH_FALLBACK_CLUB_ID;
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
        return securityEnabled ? securityContext.getRole() : LOCAL_NOAUTH_FALLBACK_ROLE;
    }

    private Map<String, Object> buildTenantPolicyContext(UUID clubId) {
        Map<String, Object> tenant = new HashMap<>();
        tenant.put("clubId", clubId.toString());

        Map<String, Object> context = new HashMap<>();
        context.put("tenant", tenant);
        return context;
    }
}
