package com.fos.sdk.events;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record SignalEnvelope(
    UUID signalId,
    SignalType type,
    String topic,
    JsonNode payload,
    String actorRef,
    String correlationId,
    Instant timestamp
) {
    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() {
        return new Builder()
            .signalId(signalId)
            .type(type)
            .topic(topic)
            .payload(payload)
            .actorRef(actorRef)
            .correlationId(correlationId)
            .timestamp(timestamp);
    }

    public static class Builder {
        private UUID signalId = UUID.randomUUID();
        private SignalType type;
        private String topic;
        private JsonNode payload;
        private String actorRef;
        private String correlationId;
        private Instant timestamp;

        public Builder signalId(UUID v)      { signalId = v;      return this; }
        public Builder type(SignalType v)     { type = v;          return this; }
        public Builder topic(String v)        { topic = v;         return this; }
        public Builder payload(JsonNode v)    { payload = v;       return this; }
        public Builder actorRef(String v)     { actorRef = v;      return this; }
        public Builder correlationId(String v){ correlationId = v; return this; }
        public Builder timestamp(Instant v)   { timestamp = v;     return this; }

        public SignalEnvelope build() {
            return new SignalEnvelope(signalId, type, topic, payload, actorRef, correlationId, timestamp);
        }
    }
}
