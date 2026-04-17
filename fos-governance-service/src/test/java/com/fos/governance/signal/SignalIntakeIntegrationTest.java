package com.fos.governance.signal;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fos.sdk.test.FosTestContainersBase;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalIntakeIntegrationTest extends FosTestContainersBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void should_accept_valid_signal_and_return_202() {
        SignalEnvelope signal = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic("fos.test.fact")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()).toString())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .payload(objectMapper.valueToTree(Map.of("test", "data")))
                .build();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/signals", signal, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void should_reject_signal_missing_topic_with_422() {
        // Signal with null topic
        SignalEnvelope signal = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic(null)   // missing — should fail schema validation
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()).toString())
                .build();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/signals", signal, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
