package com.fos.workspace.document;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.api.InitiateUploadRequest;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "fos.storage.provider=noop",
        "fos.security.enabled=false"
})
class DocumentIntegrationTest extends FosTestContainersBase {

    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12")).withReuse(true);

    private static final boolean USING_EXTERNAL_MONGO;

    static {
        boolean started = false;
        try {
            MONGO.start();
            started = true;
        } catch (Throwable ex) {
            System.err.println("[DocumentIntegrationTest] Mongo container unavailable, falling back to external MongoDB: " + ex.getMessage());
        }
        USING_EXTERNAL_MONGO = !started;
    }

    static WireMockServer wireMock;

    private static WireMockServer wireMock() {
        if (wireMock == null) {
            wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            wireMock.start();
        }
        return wireMock;
    }

    @BeforeAll
    static void startWireMock() {
        wireMock();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("fos.policy.service-url", () -> "http://localhost:" + wireMock().port());
        registry.add("fos.canonical.service-url", () -> "http://localhost:" + wireMock().port());
        registry.add("spring.data.mongodb.uri", () -> USING_EXTERNAL_MONGO
                ? Optional.ofNullable(System.getenv("MONGODB_URI")).orElse("mongodb://localhost:27017/fos_workspace")
                : MONGO.getReplicaSetUrl("fos_workspace"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkspaceDocumentRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        wireMock().resetAll();
        wireMock().stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Test
    void shouldInitiateUploadAndReturn201() {
        InitiateUploadRequest initiateRequest = new InitiateUploadRequest(
                "Test Document",
                "Description",
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "test.pdf",
                "application/pdf",
                1024L,
                null,
                null,
                List.of("test"),
                null);

        ResponseEntity<DocumentService.UploadInitiationResult> response = restTemplate.postForEntity(
                "/api/v1/documents/upload/initiate",
                initiateRequest,
                DocumentService.UploadInitiationResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().documentId()).isNotNull();
        assertThat(response.getBody().uploadUrl()).startsWith("https://noop.fos.local/upload/");
    }

    @Test
    void shouldConfirmUploadAndTransitionToActive() {
        InitiateUploadRequest initiateRequest = new InitiateUploadRequest(
                "Medical Report",
                "Annual check",
                DocumentCategory.MEDICAL,
                DocumentVisibility.TEAM_ONLY,
                "medical.pdf",
                "application/pdf",
                2048L,
                UUID.randomUUID(),
                null,
                List.of("medical"),
                "Initial upload");

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate",
                initiateRequest,
                DocumentService.UploadInitiationResult.class);

        DocumentController.ConfirmUploadWithMetadata confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(),
                initResult.objectKey(),
                "fos-workspace",
                "Medical Report",
                "Annual check",
                DocumentCategory.MEDICAL,
                DocumentVisibility.TEAM_ONLY,
                "medical.pdf",
                "application/pdf",
                2048L,
                null,
                null,
                List.of("medical"),
                "Initial upload");

        ResponseEntity<DocumentResponse> response = restTemplate.postForEntity(
                "/api/v1/documents/upload/confirm",
                confirmRequest,
                DocumentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().state().name()).isEqualTo("ACTIVE");
        assertThat(response.getBody().versionCount()).isEqualTo(1);
        assertThat(response.getBody().currentVersion()).isNotNull();
        assertThat(response.getBody().currentVersion().originalFilename()).isEqualTo("medical.pdf");
    }

    @Test
    void shouldSoftDeleteDocumentAndReturn204() {
        InitiateUploadRequest initiateRequest = new InitiateUploadRequest(
                "Delete Me",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "delete.pdf",
                "application/pdf",
                512L,
                null,
                null,
                null,
                null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate",
                initiateRequest,
                DocumentService.UploadInitiationResult.class);

        DocumentController.ConfirmUploadWithMetadata confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(),
                initResult.objectKey(),
                "fos-workspace",
                "Delete Me",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "delete.pdf",
                "application/pdf",
                512L,
                null,
                null,
                null,
                null);

        restTemplate.postForObject("/api/v1/documents/upload/confirm", confirmRequest, DocumentResponse.class);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/documents/" + initResult.documentId(),
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        DocumentResponse fetched = restTemplate.getForObject(
                "/api/v1/documents/" + initResult.documentId(),
                DocumentResponse.class);
        assertThat(fetched.state().name()).isEqualTo("ARCHIVED");
    }
}
