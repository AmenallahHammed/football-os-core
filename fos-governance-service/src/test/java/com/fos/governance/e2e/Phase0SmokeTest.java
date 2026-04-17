package com.fos.governance.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.api.ActorResponse;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.governance.policy.api.PolicyEvaluationRequest;
import com.fos.governance.signal.infrastructure.audit.AuditLogRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.test.FosTestContainersBase;
import com.fos.sdk.test.MockActorFactory;
import com.fos.sdk.test.TestActor;
import com.fos.sdk.storage.StoragePort;
import com.fos.sdk.storage.adapter.NoopStorageAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Phase0SmokeTest extends FosTestContainersBase {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().port(8181));
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("fos.opa.url", () -> "http://localhost:8181");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void full_phase0_workflow_smoke_test() {
        long initialAuditCount = auditLogRepository.count();

        // 1) Actor created
        ActorRequest request = new ActorRequest(
                "smoke-test@fos.com", "Smoke", "Test",
                ActorRole.CLUB_ADMIN, UUID.randomUUID());

        ResponseEntity<ActorResponse> actorResponse = restTemplate.postForEntity(
                "/api/v1/actors", request, ActorResponse.class);
        assertThat(actorResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID actorId = actorResponse.getBody().resourceId();

        // 2) JWT issued (test factory) and used in policy request
        TestActor testActor = MockActorFactory.clubAdmin();
        assertThat(testActor.authorizationHeader()).startsWith("Bearer ");

        stubFor(post(urlEqualTo("/v1/data/fos/allow"))
                .willReturn(okJson("{\"result\": true}")));

        PolicyEvaluationRequest policyRequest = new PolicyEvaluationRequest(
                testActor.actorId(),
                testActor.role(),
                "workspace.file.upload",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()),
                "ACTIVE",
                Map.of("source", "phase0-smoke"));

        HttpHeaders policyHeaders = new HttpHeaders();
        policyHeaders.set(HttpHeaders.AUTHORIZATION, testActor.authorizationHeader());
        ResponseEntity<PolicyResult> policyResponse = restTemplate.postForEntity(
                "/api/v1/policy/evaluate",
                new HttpEntity<>(policyRequest, policyHeaders),
                PolicyResult.class);
        assertThat(policyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(policyResponse.getBody()).isNotNull();
        assertThat(policyResponse.getBody().isAllowed()).isTrue();

        // 3) File upload via sdk-storage
        StoragePort storage = new NoopStorageAdapter();
        String objectKey = "phase0/smoke-" + UUID.randomUUID() + ".txt";
        var upload = storage.generateUploadUrl(
                "fos-smoke",
                objectKey,
                "text/plain",
                Duration.ofMinutes(10));
        storage.confirmUpload("fos-smoke", objectKey);

        assertThat(upload.uploadUrl()).contains(objectKey);
        assertThat(upload.objectKey()).isEqualTo(objectKey);

        // 4) Signal intake to trigger async audit pipeline
        SignalEnvelope signal = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic("fos.smoke.test.event")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .payload(objectMapper.valueToTree(Map.of(
                        "status", "success",
                        "uploadedObjectKey", objectKey,
                        "actorId", actorId.toString())))
                .build();

        ResponseEntity<Void> signalResponse = restTemplate.postForEntity(
                "/api/v1/signals", signal, Void.class);
        assertThat(signalResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 5) Audit logged (deterministic wait)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(auditLogRepository.count())
                        .as("Signal should persist to audit log")
                        .isGreaterThan(initialAuditCount));
    }
}
