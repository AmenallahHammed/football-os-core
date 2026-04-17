package com.fos.governance.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.api.ActorResponse;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.governance.signal.infrastructure.audit.AuditLogRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.test.FosTestContainersBase;
import com.fos.sdk.test.MockActorFactory;
import com.fos.sdk.test.SignalCaptor;
import com.fos.sdk.test.TestActor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Phase0SmokeTest extends FosTestContainersBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void full_phase0_workflow_smoke_test() {
        // 1. Create Actor (OIDC Identity Provider would have registered this)
        ActorRequest request = new ActorRequest(
                "smoke-test@fos.com", "Smoke", "Test",
                ActorRole.CLUB_ADMIN, UUID.randomUUID());

        ResponseEntity<ActorResponse> actorResponse = restTemplate.postForEntity(
                "/api/v1/actors", request, ActorResponse.class);
        assertThat(actorResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID actorId = actorResponse.getBody().resourceId();

        // 2. Simulate Signal Intake (triggered by some system action)
        SignalEnvelope signal = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic("fos.smoke.test.event")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, actorId).toString())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .payload(objectMapper.valueToTree(Map.of("status", "success")))
                .build();

        ResponseEntity<Void> signalResponse = restTemplate.postForEntity(
                "/api/v1/signals", signal, Void.class);
        assertThat(signalResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 3. Verify Auditing (Async persistence check)
        // Wait up to 5s for the consumer to save to DB
        long start = System.currentTimeMillis();
        boolean saved = false;
        while (System.currentTimeMillis() - start < 5000) {
            if (auditLogRepository.count() > 0) {
                saved = true;
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
        assertThat(saved).as("Signal should be eventually persistent in audit log").isTrue();
    }
}
