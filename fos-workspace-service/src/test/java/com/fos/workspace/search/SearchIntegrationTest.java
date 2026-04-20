package com.fos.workspace.search;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.event.domain.EventType;
import com.fos.workspace.event.domain.WorkspaceEvent;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import com.fos.workspace.search.api.SearchResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",
    "spring.security.enabled=false"
})
class SearchIntegrationTest extends FosTestContainersBase {

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
        documentRepository.deleteAll();
        eventRepository.deleteAll();
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkspaceDocumentRepository documentRepository;

    @Autowired
    private WorkspaceEventRepository eventRepository;

    @Test
    void should_return_search_results_for_matching_document_name() {
        // Upload a document with a unique searchable name
        String uniqueName = "UniqueSearchableName_" + System.currentTimeMillis();
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                uniqueName, null,
                DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                "search_test.pdf", "application/pdf", 1024L,
                null, null, List.of(), null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate", initiateRequest,
                DocumentService.UploadInitiationResult.class);

        restTemplate.postForObject("/api/v1/documents/upload/confirm",
                new DocumentController.ConfirmUploadWithMetadata(
                        initResult.documentId(), initResult.objectKey(), "fos-workspace",
                        uniqueName, null,
                        DocumentCategory.GENERAL, DocumentVisibility.CLUB_WIDE,
                        "search_test.pdf", "application/pdf", 1024L,
                        null, null, List.of(), null),
                DocumentResponse.class);

        // Search for it
        ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                "/api/v1/search?q=" + uniqueName, SearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalDocuments()).isGreaterThanOrEqualTo(1);
        assertThat(response.getBody().documents())
                .anyMatch(d -> d.name().equals(uniqueName));
    }

    @Test
    void should_return_empty_when_no_match() {
        ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                "/api/v1/search?q=ZZZNOMATCHEXPECTED99999", SearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalDocuments()).isEqualTo(0);
        assertThat(response.getBody().totalEvents()).isEqualTo(0);
    }

    @Test
    void should_include_matching_events_in_search_results() {
        String eventTitle = "UniqueSearchEvent_" + System.currentTimeMillis();
        WorkspaceEvent event = WorkspaceEvent.create(
                eventTitle,
                "Searchable event description",
                EventType.TRAINING,
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                "Pitch A",
                CanonicalRef.of(CanonicalType.CLUB, UUID.fromString("00000000-0000-0000-0000-000000000001")),
                CanonicalRef.of(CanonicalType.TEAM, UUID.fromString("10000000-0000-0000-0000-000000000001")));
        eventRepository.save(event);

        ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                "/api/v1/search?q=" + eventTitle, SearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().totalEvents()).isGreaterThanOrEqualTo(1);
        assertThat(response.getBody().events())
                .anyMatch(e -> eventTitle.equals(e.title()));
    }

    @Test
    void should_filter_documents_by_policy_permissions() {
        String sharedPrefix = "PermissionFilter_" + System.currentTimeMillis();
        uploadDocument(sharedPrefix + "_GENERAL", DocumentCategory.GENERAL);
        uploadDocument(sharedPrefix + "_MEDICAL", DocumentCategory.MEDICAL);

        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .withRequestBody(containing("\"action\":\"workspace.document.general.read\""))
                .atPriority(1)
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"general allowed\"}")));
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .atPriority(10)
                .willReturn(okJson("{\"decision\":\"DENY\",\"reason\":\"category denied\"}")));

        ResponseEntity<SearchResponse> response = restTemplate.getForEntity(
                "/api/v1/search?q=" + sharedPrefix, SearchResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().documents())
                .anyMatch(d -> d.category() == DocumentCategory.GENERAL);
        assertThat(response.getBody().documents())
                .noneMatch(d -> d.category() == DocumentCategory.MEDICAL);
    }

    private void uploadDocument(String name, DocumentCategory category) {
        var initiateRequest = new com.fos.workspace.document.api.InitiateUploadRequest(
                name, null,
                category, DocumentVisibility.CLUB_WIDE,
                "search_test.pdf", "application/pdf", 1024L,
                null, null, List.of(), null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate", initiateRequest,
                DocumentService.UploadInitiationResult.class);

        restTemplate.postForObject("/api/v1/documents/upload/confirm",
                new DocumentController.ConfirmUploadWithMetadata(
                        initResult.documentId(), initResult.objectKey(), "fos-workspace",
                        name, null,
                        category, DocumentVisibility.CLUB_WIDE,
                        "search_test.pdf", "application/pdf", 1024L,
                        null, null, List.of(), null),
                DocumentResponse.class);
    }
}
