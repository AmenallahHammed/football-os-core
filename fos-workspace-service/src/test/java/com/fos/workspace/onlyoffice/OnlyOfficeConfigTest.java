package com.fos.workspace.onlyoffice;

import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.document.api.DocumentController;
import com.fos.workspace.document.api.DocumentResponse;
import com.fos.workspace.document.api.InitiateUploadRequest;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "fos.storage.provider=noop",
        "fos.security.enabled=false",
        "fos.onlyoffice.document-server-url=http://localhost:8090",
        "fos.onlyoffice.jwt-secret=test-secret-key-must-be-32-chars!!"
})
class OnlyOfficeConfigTest extends FosTestContainersBase {

    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12")).withReuse(true);

    private static final boolean USING_EXTERNAL_MONGO;

    static {
        boolean started = false;
        try {
            MONGO.start();
            started = true;
        } catch (Throwable ex) {
            System.err.println("[OnlyOfficeConfigTest] Mongo container unavailable, falling back to external MongoDB: " + ex.getMessage());
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
        registry.add("spring.data.mongodb.uri", () -> USING_EXTERNAL_MONGO
                ? Optional.ofNullable(System.getenv("MONGODB_URI")).orElse("mongodb://localhost:27017/fos_workspace")
                : MONGO.getReplicaSetUrl("fos_workspace"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkspaceDocumentRepository repository;

    @MockBean
    private FosKafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        wireMock().resetAll();
        wireMock().stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Test
    void should_generate_onlyoffice_config_with_token() {
        InitiateUploadRequest initiateRequest = new InitiateUploadRequest(
                "Test DOCX",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                4096L,
                null,
                null,
                List.of(),
                null);

        DocumentService.UploadInitiationResult initResult = restTemplate.postForObject(
                "/api/v1/documents/upload/initiate",
                initiateRequest,
                DocumentService.UploadInitiationResult.class);

        DocumentController.ConfirmUploadWithMetadata confirmRequest = new DocumentController.ConfirmUploadWithMetadata(
                initResult.documentId(),
                initResult.objectKey(),
                "fos-workspace",
                "Test DOCX",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                4096L,
                null,
                null,
                List.of(),
                null);

        DocumentResponse confirmed = restTemplate.postForObject(
                "/api/v1/documents/upload/confirm",
                confirmRequest,
                DocumentResponse.class);

        OnlyOfficeConfigRequest configRequest = new OnlyOfficeConfigRequest(confirmed.documentId(), "view");

        ResponseEntity<OnlyOfficeConfigResponse> response = restTemplate.postForEntity(
                "/api/v1/onlyoffice/config",
                configRequest,
                OnlyOfficeConfigResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().documentServerUrl()).isEqualTo("http://localhost:8090");
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().config().document().fileType()).isEqualTo("docx");
        assertThat(response.getBody().config().document().url()).isNotBlank();
    }
}
