package com.fos.sdk.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class AbstractFosConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_call_handle_with_deserialized_envelope() throws Exception {
        var received = new AtomicReference<SignalEnvelope>();

        AbstractFosConsumer consumer = new AbstractFosConsumer(objectMapper) {
            @Override
            protected void handle(SignalEnvelope envelope) {
                received.set(envelope);
            }
        };

        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
            .correlationId("corr-001")
            .payload(objectMapper.createObjectNode().put("actorId", "a-001"))
            .build();

        String json = objectMapper.writeValueAsString(envelope);
        var record = new ConsumerRecord<>(KafkaTopics.IDENTITY_ACTOR_CREATED, 0, 0L, "key", json);

        consumer.onMessage(record);

        assertThat(received.get()).isNotNull();
        assertThat(received.get().correlationId()).isEqualTo("corr-001");
        assertThat(received.get().type()).isEqualTo(SignalType.FACT);
    }

    @Test
    void should_not_throw_when_handle_raises_exception() throws Exception {
        AbstractFosConsumer consumer = new AbstractFosConsumer(objectMapper) {
            @Override
            protected void handle(SignalEnvelope envelope) {
                throw new RuntimeException("domain error");
            }
        };

        var envelope = SignalEnvelope.builder()
            .type(SignalType.FACT)
            .topic("test.topic")
            .payload(objectMapper.createObjectNode())
            .build();

        String json = objectMapper.writeValueAsString(envelope);
        var record = new ConsumerRecord<>("test.topic", 0, 0L, "key", json);

        // Should not propagate — error handling is the base class responsibility
        assertThatCode(() -> consumer.onMessage(record)).doesNotThrowAnyException();
    }
}
