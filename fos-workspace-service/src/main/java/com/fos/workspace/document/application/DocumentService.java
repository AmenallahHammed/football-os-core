package com.fos.workspace.document.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.fos.workspace.config.ConflictException;
import com.fos.workspace.document.api.ConfirmUploadRequest;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.api.InitiateUploadRequest;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(15);
    public static final String DUPLICATE_UPLOAD_CONFIRMATION_MESSAGE = "Upload was already confirmed for this document.";
    // Local no-auth development fallback values only.
    private static final UUID LOCAL_NOAUTH_FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LOCAL_NOAUTH_FALLBACK_CLUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String LOCAL_NOAUTH_FALLBACK_ROLE = "ROLE_CLUB_ADMIN";
    private static final String DOCUMENT_UPLOADED_TOPIC = "fos.workspace.document.uploaded";

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final PolicyClient policyClient;
    private final FosKafkaProducer kafkaProducer;
    private final FosSecurityContext securityContext;
    private final ObjectMapper objectMapper;
    private final String workspaceBucket;
    private final boolean securityEnabled;

    public DocumentService(WorkspaceDocumentRepository documentRepository,
                           StoragePort storagePort,
                           PolicyClient policyClient,
                           FosKafkaProducer kafkaProducer,
                           FosSecurityContext securityContext,
                           ObjectMapper objectMapper,
                           @Value("${minio.bucket:fos-workspace}") String workspaceBucket,
                           @Value("${fos.security.enabled:true}") boolean securityEnabled) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.policyClient = policyClient;
        this.kafkaProducer = kafkaProducer;
        this.securityContext = securityContext;
        this.objectMapper = objectMapper;
        this.workspaceBucket = workspaceBucket;
        this.securityEnabled = securityEnabled;
    }

    public UploadInitiationResult initiateUpload(InitiateUploadRequest request) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        CanonicalRef ownerRef = CanonicalRef.club(clubId);

        authorize(actionFor(request.category(), "upload"), ownerRef, ResourceState.DRAFT.name(), actorId, role, clubId);

        WorkspaceDocument document = WorkspaceDocument.create(
                request.name(),
                request.description(),
                request.category(),
                request.visibility(),
                ownerRef,
                request.linkedPlayerRefId() != null ? CanonicalRef.of(CanonicalType.PLAYER, request.linkedPlayerRefId()) : null,
                request.linkedTeamRefId() != null ? CanonicalRef.of(CanonicalType.TEAM, request.linkedTeamRefId()) : null,
                request.tags());

        WorkspaceDocument saved = documentRepository.save(document);
        String objectKey = objectKeyFor(clubId, saved.getResourceId(), saved.nextVersionNumber(), request.originalFilename());
        PresignedUploadUrl uploadUrl;
        try {
            uploadUrl = storagePort.generateUploadUrl(
                    workspaceBucket,
                    objectKey,
                    request.contentType(),
                    UPLOAD_URL_EXPIRY);
        } catch (RuntimeException ex) {
            log.warn("Upload initiation failed before presigned URL generation completed: documentId={} category={} actor={} objectKey={} reason={}",
                    saved.getResourceId(), request.category(), actorId, objectKey, ex.getMessage());
            cleanupFailedDraft(saved, actorId, request.category(), ex);
            throw ex;
        }

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(saved.getOwnerRef().toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "documentId", saved.getResourceId().toString(),
                        "event", "document_upload_initiated",
                        "category", saved.getCategory().name(),
                        "objectKey", objectKey,
                        "uploaderActorId", actorId.toString()
                )))
                .build());

        log.info("Initiated upload: documentId={} category={} actor={} objectKey={}",
                saved.getResourceId(), request.category(), actorId, objectKey);
        return new UploadInitiationResult(saved.getResourceId(), uploadUrl.uploadUrl(), objectKey);
    }

    public DocumentResponse confirmUpload(ConfirmUploadRequest request, InitiateUploadRequest originalRequest) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();

        WorkspaceDocument document = loadDocument(request.documentId(), clubId);
        if (document.currentVersion() != null || document.getState() == ResourceState.ACTIVE) {
            throw new ConflictException(DUPLICATE_UPLOAD_CONFIRMATION_MESSAGE);
        }
        if (document.getState() == ResourceState.ARCHIVED) {
            throw new ConflictException("Cannot confirm upload for archived document: " + request.documentId());
        }

        storagePort.confirmUpload(request.storageBucket(), request.storageObjectKey());

        DocumentVersion version = new DocumentVersion(
                request.storageObjectKey(),
                request.storageBucket(),
                originalRequest.originalFilename(),
                originalRequest.contentType(),
                originalRequest.fileSizeBytes(),
                document.nextVersionNumber(),
                actorId,
                originalRequest.versionNote());

        document.addVersion(version);
        WorkspaceDocument saved = documentRepository.save(document);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(saved.getOwnerRef().toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "documentId", saved.getResourceId().toString(),
                        "version", version.getVersionNumber(),
                        "category", saved.getCategory().name(),
                        "event", "document_upload_confirmed",
                        "uploaderActorId", actorId.toString()
                )))
                .build());

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(DOCUMENT_UPLOADED_TOPIC)
                .actorRef(saved.getOwnerRef().toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "documentId", saved.getResourceId().toString(),
                        "versionId", version.getVersionId().toString(),
                        "objectKey", version.getStorageObjectKey(),
                        "bucket", version.getStorageBucket(),
                        "uploaderActorId", actorId.toString()
                )))
                .build());

        log.info("Confirmed upload: documentId={} version={} actor={}", saved.getResourceId(), version.getVersionNumber(), actorId);
        return DocumentResponse.from(saved, generateDownloadUrl(saved));
    }

    public DocumentResponse getDocument(UUID documentId) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        WorkspaceDocument document = loadDocument(documentId, clubId);
        authorize(actionFor(document.getCategory(), "read"), document.getOwnerRef(), document.getState().name(), actorId, role, clubId);
        return DocumentResponse.from(document, generateDownloadUrl(document));
    }

    public Page<DocumentResponse> listDocuments(DocumentCategory category, Pageable pageable) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        authorize(actionFor(category, "read"), CanonicalRef.club(clubId), ResourceState.ACTIVE.name(), actorId, role, clubId);

        return documentRepository.findByOwnerRefIdAndCategoryAndState(clubId, category, ResourceState.ACTIVE, pageable)
                .map(document -> DocumentResponse.from(document, generateDownloadUrl(document)));
    }

    public void softDeleteDocument(UUID documentId) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        WorkspaceDocument document = loadDocument(documentId, clubId);
        authorize(actionFor(document.getCategory(), "delete"), document.getOwnerRef(), document.getState().name(), actorId, role, clubId);

        document.softDelete();
        WorkspaceDocument saved = documentRepository.save(document);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(saved.getOwnerRef().toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "documentId", saved.getResourceId().toString(),
                        "event", "document_archived",
                        "state", saved.getState().name()
                )))
                .build());

        log.info("Soft-deleted document: documentId={} actor={}", documentId, actorId);
    }

    private WorkspaceDocument loadDocument(UUID documentId, UUID clubId) {
        return documentRepository.findByResourceIdAndOwnerRefId(documentId, clubId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));
    }

    private void cleanupFailedDraft(WorkspaceDocument document,
                                    UUID actorId,
                                    DocumentCategory category,
                                    RuntimeException cause) {
        try {
            documentRepository.deleteById(document.getId());
            log.warn("Rolled back failed upload initiation: documentId={} category={} actor={} reason={}",
                    document.getResourceId(), category, actorId, cause.getMessage());
        } catch (RuntimeException cleanupEx) {
            log.error("Failed to roll back draft after upload initiation error: documentId={} category={} actor={}",
                    document.getResourceId(), category, actorId, cleanupEx);
        }
    }

    private void authorize(String action,
                           CanonicalRef resourceRef,
                           String resourceState,
                           UUID actorId,
                           String role,
                           UUID clubId) {
        PolicyResult policy = policyClient.evaluate(PolicyRequest.withContext(
                actorId,
                role,
                action,
                resourceRef,
                resourceState,
                buildTenantPolicyContext(clubId)));
        if (!policy.isAllowed()) {
            throw new AccessDeniedException(policy.reason());
        }
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

    private String actionFor(DocumentCategory category, String operation) {
        return "workspace.document." + category.name().toLowerCase() + "." + operation;
    }

    private String objectKeyFor(UUID clubId, UUID documentId, int versionNumber, String originalFilename) {
        String extension = extensionForStorage(originalFilename);
        String sanitizedBaseName = sanitizeFilenameForStorage(baseName(originalFilename));
        String uniqueSegment = UUID.randomUUID().toString();

        return "documents/"
                + clubId
                + "/"
                + documentId
                + "/"
                + sanitizedBaseName
                + "-v"
                + versionNumber
                + "-"
                + uniqueSegment
                + extension;
    }

    private String baseName(String originalFilename) {
        String trimmed = originalFilename == null ? "" : originalFilename.trim();
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex <= 0) {
            return trimmed;
        }
        return trimmed.substring(0, dotIndex);
    }

    private String extensionForStorage(String originalFilename) {
        String trimmed = originalFilename == null ? "" : originalFilename.trim();
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == trimmed.length() - 1) {
            return "";
        }

        String extension = trimmed.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return extension.isBlank() ? "" : "." + extension;
    }

    private String sanitizeFilenameForStorage(String originalFilename) {
        String normalized = Normalizer.normalize(originalFilename == null ? "" : originalFilename, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String sanitized = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("['`]+", "-")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        return sanitized.isBlank() ? "document" : sanitized;
    }

    private String generateDownloadUrl(WorkspaceDocument document) {
        DocumentVersion currentVersion = document.currentVersion();
        if (currentVersion == null) {
            return null;
        }
        return storagePort.generateDownloadUrl(
                currentVersion.getStorageBucket(),
                currentVersion.getStorageObjectKey(),
                DOWNLOAD_URL_EXPIRY);
    }

    public record UploadInitiationResult(UUID documentId, String uploadUrl, String objectKey) {
    }
}
