package com.fos.sdk.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * Decorator around KafkaTemplate. Transparently enriches every SignalEnvelope
 * with correlationId and timestamp before sending.
 */
@Component
public class FosKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(FosKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FosKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void emit(SignalEnvelope envelope) {
        var enriched = envelope.toBuilder()
            .correlationId(RequestContext.get())
            .timestamp(Instant.now())
            .build();

        try {
            String json = objectMapper.writeValueAsString(enriched);
            log.info("Emitting signal: type={}, topic={}, id={}", enriched.type(), enriched.topic(), enriched.signalId());
            kafkaTemplate.send(enriched.topic(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize signal envelope", e);
            throw new RuntimeException("Signal serialization failed", e);
        }
    }
}
