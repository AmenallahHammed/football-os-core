package com.fos.sdk.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FosKafkaProducerTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private FosKafkaProducer producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Handle Java 8 dates/times
        producer = new FosKafkaProducer(kafkaTemplate, objectMapper);
    }

    @Test
    void should_enrich_envelope_with_correlation_id_before_sending() {
        RequestContext.set("req-abc-123");
        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
            .actorRef("actor-001")
            .payload(objectMapper.createObjectNode().put("name", "test"))
            .build();

        producer.emit(envelope);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.IDENTITY_ACTOR_CREATED), captor.capture());
        assertThat(captor.getValue()).contains("req-abc-123");

        RequestContext.clear();
    }

    @Test
    void should_use_fallback_correlation_id_when_context_is_empty() {
        RequestContext.clear();
        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
            .payload(objectMapper.createObjectNode())
            .build();

        producer.emit(envelope);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.IDENTITY_ACTOR_CREATED), captor.capture());
        assertThat(captor.getValue()).contains("no-correlation-id");
    }
}
