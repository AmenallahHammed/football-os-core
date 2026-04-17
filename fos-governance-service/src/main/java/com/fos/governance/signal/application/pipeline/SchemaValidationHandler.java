package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaValidationHandler extends SignalHandler {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationHandler.class);

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        if (signal.type() == null || signal.topic() == null || signal.topic().isBlank()) {
            log.warn("Signal rejected — missing type or topic: signalId={}", signal.signalId());
            return null;
        }
        if (signal.actorRef() == null) {
            log.warn("Signal rejected — missing actorRef: signalId={}", signal.signalId());
            return null;
        }
        return signal;
    }
}
