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

        // -- Search documents ------------------------------------------------
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

        // -- Search events ---------------------------------------------------
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
