package com.fos.workspace.profile.application;

import com.fos.sdk.canonical.CanonicalRef;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PlayerProfileService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);
    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FALLBACK_ROLE = "ROLE_CLUB_ADMIN";

    private final WorkspaceDocumentRepository documentRepository;
    private final StoragePort storagePort;
    private final PolicyClient policyClient;
    private final CanonicalResolver canonicalResolver;
    private final FosSecurityContext securityContext;
    private final boolean securityEnabled;

    public PlayerProfileService(WorkspaceDocumentRepository documentRepository,
                                StoragePort storagePort,
                                PolicyClient policyClient,
                                CanonicalResolver canonicalResolver,
                                FosSecurityContext securityContext,
                                @Value("${fos.security.enabled:true}") boolean securityEnabled) {
        this.documentRepository = documentRepository;
        this.storagePort = storagePort;
        this.policyClient = policyClient;
        this.canonicalResolver = canonicalResolver;
        this.securityContext = securityContext;
        this.securityEnabled = securityEnabled;
    }

    public PlayerProfileResponse getProfile(UUID playerId) {
        UUID actorId = currentActorId();
        String role = currentActorRole();

        PlayerDTO player = canonicalResolver.getPlayer(playerId);

        boolean canSeeDocuments = canAccess(actorId, role, "workspace.profile.tab.documents", playerId);
        boolean canSeeReports = canAccess(actorId, role, "workspace.profile.tab.reports", playerId);
        boolean canSeeMedical = canAccess(actorId, role, "workspace.profile.tab.medical", playerId);
        boolean canSeeAdmin = canAccess(actorId, role, "workspace.profile.tab.admin", playerId);

        List<WorkspaceDocument> linkedDocuments = documentRepository.findByLinkedPlayerRefIdAndState(playerId, ResourceState.ACTIVE)
                .stream()
                .sorted(Comparator.comparing(WorkspaceDocument::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        List<DocumentResponse> documents = canSeeDocuments
                ? mapCategory(linkedDocuments, DocumentCategory.GENERAL)
                : null;
        List<DocumentResponse> reports = canSeeReports
                ? mapCategory(linkedDocuments, DocumentCategory.REPORT)
                : null;
        List<DocumentResponse> medicalRecords = canSeeMedical
                ? mapCategory(linkedDocuments, DocumentCategory.MEDICAL)
                : null;
        List<DocumentResponse> adminDocs = canSeeAdmin
                ? mergeLists(
                mapCategory(linkedDocuments, DocumentCategory.ADMIN),
                mapCategory(linkedDocuments, DocumentCategory.CONTRACT))
                : null;

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

    private boolean canAccess(UUID actorId, String role, String action, UUID playerId) {
        PolicyResult result = policyClient.evaluate(PolicyRequest.of(
                actorId,
                role,
                action,
                CanonicalRef.player(playerId),
                ResourceState.ACTIVE.name()));
        return result.isAllowed();
    }

    private List<DocumentResponse> mapCategory(List<WorkspaceDocument> documents, DocumentCategory category) {
        return documents.stream()
                .filter(document -> document.getCategory() == category)
                .map(document -> {
                    String downloadUrl = document.currentVersion() != null
                            ? storagePort.generateDownloadUrl(
                            document.currentVersion().getStorageBucket(),
                            document.currentVersion().getStorageObjectKey(),
                            DOWNLOAD_URL_EXPIRY)
                            : null;
                    return DocumentResponse.from(document, downloadUrl);
                })
                .toList();
    }

    @SafeVarargs
    private final <T> List<T> mergeLists(List<T>... lists) {
        return java.util.Arrays.stream(lists)
                .filter(list -> list != null)
                .flatMap(List::stream)
                .toList();
    }

    private UUID currentActorId() {
        return securityEnabled ? securityContext.getActorId() : FALLBACK_ACTOR_ID;
    }

    private String currentActorRole() {
        return securityEnabled ? securityContext.getRole() : FALLBACK_ROLE;
    }
}
