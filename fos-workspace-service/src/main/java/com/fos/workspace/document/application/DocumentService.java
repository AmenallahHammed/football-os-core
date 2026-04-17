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

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(15);
    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FALLBACK_ROLE = "ROLE_CLUB_ADMIN";
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
        String role = currentActorRole();
        CanonicalRef ownerRef = CanonicalRef.of(CanonicalType.CLUB, actorId);

        authorize(actionFor(request.category(), "upload"), ownerRef, ResourceState.DRAFT.name(), actorId, role);

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
        String objectKey = objectKeyFor(saved.getResourceId(), saved.nextVersionNumber(), request.originalFilename());
        PresignedUploadUrl uploadUrl = storagePort.generateUploadUrl(
                workspaceBucket,
                objectKey,
                request.contentType(),
                UPLOAD_URL_EXPIRY);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(saved.getOwnerRef().toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "documentId", saved.getResourceId().toString(),
                        "event", "document_upload_initiated",
                        "category", saved.getCategory().name(),
                        "objectKey", objectKey
                )))
                .build());

        log.info("Initiated upload: documentId={} category={} actor={}", saved.getResourceId(), request.category(), actorId);
        return new UploadInitiationResult(saved.getResourceId(), uploadUrl.uploadUrl(), objectKey);
    }

    public DocumentResponse confirmUpload(ConfirmUploadRequest request, InitiateUploadRequest originalRequest) {
        UUID actorId = currentActorId();

        WorkspaceDocument document = loadDocument(request.documentId());
        if (document.getState() == ResourceState.ARCHIVED) {
            throw new IllegalStateException("Cannot confirm upload for archived document: " + request.documentId());
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
                        "event", "document_upload_confirmed"
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
                        "bucket", version.getStorageBucket()
                )))
                .build());

        log.info("Confirmed upload: documentId={} version={} actor={}", saved.getResourceId(), version.getVersionNumber(), actorId);
        return DocumentResponse.from(saved, generateDownloadUrl(saved));
    }

    public DocumentResponse getDocument(UUID documentId) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        WorkspaceDocument document = loadDocument(documentId);
        authorize(actionFor(document.getCategory(), "read"), document.getOwnerRef(), document.getState().name(), actorId, role);
        return DocumentResponse.from(document, generateDownloadUrl(document));
    }

    public Page<DocumentResponse> listDocuments(DocumentCategory category, Pageable pageable) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        authorize(actionFor(category, "read"), CanonicalRef.of(CanonicalType.CLUB, actorId), ResourceState.ACTIVE.name(), actorId, role);

        return documentRepository.findByCategoryAndState(category, ResourceState.ACTIVE, pageable)
                .map(document -> DocumentResponse.from(document, generateDownloadUrl(document)));
    }

    public void softDeleteDocument(UUID documentId) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        WorkspaceDocument document = loadDocument(documentId);
        authorize(actionFor(document.getCategory(), "delete"), document.getOwnerRef(), document.getState().name(), actorId, role);

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

    private WorkspaceDocument loadDocument(UUID documentId) {
        return documentRepository.findByResourceId(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));
    }

    private void authorize(String action,
                           CanonicalRef resourceRef,
                           String resourceState,
                           UUID actorId,
                           String role) {
        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(actorId, role, action, resourceRef, resourceState));
        if (!policy.isAllowed()) {
            throw new AccessDeniedException(policy.reason());
        }
    }

    private UUID currentActorId() {
        return securityEnabled ? securityContext.getActorId() : FALLBACK_ACTOR_ID;
    }

    private String currentActorRole() {
        return securityEnabled ? securityContext.getRole() : FALLBACK_ROLE;
    }

    private String actionFor(DocumentCategory category, String operation) {
        return "workspace.document." + category.name().toLowerCase() + "." + operation;
    }

    private String objectKeyFor(UUID documentId, int versionNumber, String originalFilename) {
        return "documents/" + documentId + "/v" + versionNumber + "_" + originalFilename;
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
