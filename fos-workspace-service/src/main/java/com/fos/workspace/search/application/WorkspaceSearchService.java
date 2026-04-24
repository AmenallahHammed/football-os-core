package com.fos.workspace.search.application;

import com.fos.sdk.canonical.CanonicalRef;
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
import com.fos.workspace.event.domain.WorkspaceEvent;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import com.fos.workspace.search.api.SearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workspace search service.
 *
 * Searches across documents and events. Results are filtered by the
 * caller's permissions - categories the actor cannot see are excluded.
 *
 * Phase 1: Uses MongoDB $regex.
 * Phase 2+: Replace with OpenSearch/Elasticsearch for full-text search,
 *           relevance scoring, and highlighting.
 */
@Service
public class WorkspaceSearchService {

    private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofHours(1);
    private static final int MAX_SEARCH_RESULTS = 50;
    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FALLBACK_CLUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FALLBACK_ROLE = "ROLE_CLUB_ADMIN";

    private final WorkspaceDocumentRepository documentRepository;
    private final WorkspaceEventRepository eventRepository;
    private final PolicyClient policyClient;
    private final StoragePort storagePort;
    private final FosSecurityContext securityContext;
    private final boolean securityEnabled;

    public WorkspaceSearchService(WorkspaceDocumentRepository documentRepository,
                                   WorkspaceEventRepository eventRepository,
                                   PolicyClient policyClient,
                                   StoragePort storagePort,
                                   FosSecurityContext securityContext,
                                   @Value("${fos.security.enabled:true}") boolean securityEnabled) {
        this.documentRepository = documentRepository;
        this.eventRepository = eventRepository;
        this.policyClient = policyClient;
        this.storagePort = storagePort;
        this.securityContext = securityContext;
        this.securityEnabled = securityEnabled;
    }

    public SearchResponse search(String query) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();

        // -- Search documents ------------------------------------------------
        // Use the regex search method from WorkspaceDocumentRepository
        Page<WorkspaceDocument> docResults = documentRepository.searchByOwnerRefIdAndName(
                clubId, query, PageRequest.of(0, MAX_SEARCH_RESULTS));

        // Filter documents to only include categories the actor can see
        List<DocumentResponse> permittedDocs = docResults.stream()
                .filter(doc -> canAccessCategory(actorId, role, clubId, doc.getCategory()))
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

        // -- Search events ---------------------------------------------------
        // Simple: search by title in active events
        // TODO Phase 2: replace with full-text search via OpenSearch
        List<EventResponse> matchingEvents = eventRepository
                .searchActiveByClubAndTitle(clubId, query).stream()
                .filter(event -> canAccessEvent(actorId, role, clubId, event))
                .limit(MAX_SEARCH_RESULTS)
                .map(EventResponse::from)
                .toList();

        return new SearchResponse(query, permittedDocs, matchingEvents,
                permittedDocs.size(), matchingEvents.size());
    }

    private boolean canAccessCategory(UUID actorId, String role, UUID clubId, DocumentCategory category) {
        String action = "workspace.document." + category.name().toLowerCase() + ".read";
        return policyClient.evaluate(PolicyRequest.withContext(
                actorId,
                role,
                action,
                CanonicalRef.club(clubId),
                ResourceState.ACTIVE.name(),
                buildTenantPolicyContext(clubId))).isAllowed();
    }

    private boolean canAccessEvent(UUID actorId, String role, UUID clubId, WorkspaceEvent event) {
        CanonicalRef resourceRef = event.getTeamRef() != null ? event.getTeamRef() : CanonicalRef.club(clubId);
        return policyClient.evaluate(PolicyRequest.withContext(
                actorId,
                role,
                "workspace.event.read",
                resourceRef,
                event.getState().name(),
                buildTenantPolicyContext(clubId))).isAllowed();
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
