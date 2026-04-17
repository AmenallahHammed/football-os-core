package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;

/**
 * Abstract handler in the Chain of Responsibility for signal processing.
 */
public abstract class SignalHandler {

    private SignalHandler next;

    public SignalHandler then(SignalHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Runs this handler then passes result to next, unless result is null (rejected).
     */
    public final SignalEnvelope process(SignalEnvelope signal) {
        SignalEnvelope result = handle(signal);
        if (result == null) return null;
        return (next != null) ? next.process(result) : result;
    }

    protected abstract SignalEnvelope handle(SignalEnvelope signal);
}
