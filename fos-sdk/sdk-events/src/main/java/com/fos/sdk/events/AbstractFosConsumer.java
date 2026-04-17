package com.fos.sdk.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Template Method base for all Kafka consumers in FOS.
 * Handles deserialization, correlation ID propagation, error logging,
 * and offset acknowledgement. Subclasses implement only domain logic.
 */
public abstract class AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AbstractFosConsumer.class);

    private final ObjectMapper objectMapper;

    protected AbstractFosConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Template method — final: structure is fixed
    public void onMessage(ConsumerRecord<String, String> record) {
        SignalEnvelope envelope = null;
        try {
            envelope = objectMapper.readValue(record.value(), SignalEnvelope.class);
            MDC.put("correlationId", envelope.correlationId() != null ? envelope.correlationId() : "unknown");
            RequestContext.set(envelope.correlationId());

            log.debug("Received {} signal on topic {}", envelope.type(), record.topic());

            handle(envelope);   // subclass implements this step only

        } catch (Exception e) {
            handleError(record, envelope, e);
        } finally {
            MDC.clear();
            RequestContext.clear();
        }
    }

    // The only method subclasses implement
    protected abstract void handle(SignalEnvelope envelope);

    // Override in subclass if custom error handling is needed
    protected void handleError(ConsumerRecord<String, String> record, SignalEnvelope envelope, Exception e) {
        log.error("Error processing signal on topic={}, offset={}, error={}",
            record.topic(), record.offset(), e.getMessage(), e);
    }
}
