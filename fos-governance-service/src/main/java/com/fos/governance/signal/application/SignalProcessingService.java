package com.fos.governance.signal.application;

import com.fos.governance.signal.application.pipeline.SignalHandler;
import com.fos.sdk.events.SignalEnvelope;
import org.springframework.stereotype.Service;

@Service
public class SignalProcessingService {

    private final SignalHandler pipeline;

    public SignalProcessingService(SignalHandler pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Runs the signal through the full Chain of Responsibility pipeline.
     */
    public SignalEnvelope process(SignalEnvelope signal) {
        return pipeline.process(signal);
    }
}
