package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeClassificationHandler extends SignalHandler {

    private static final Logger log = LoggerFactory.getLogger(TypeClassificationHandler.class);

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        log.debug("Signal classified: type={} topic={}", signal.type(), signal.topic());
        return signal;
    }
}
