package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;

/**
 * Attaches actor display name to the signal context for use in notifications.
 * Placeholder for Sprint 0.4.
 */
public class ActorEnrichmentHandler extends SignalHandler {

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        return signal;
    }
}
