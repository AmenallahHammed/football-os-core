package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.SignalEnvelope;

public class KafkaRoutingHandler extends SignalHandler {

    private final FosKafkaProducer kafkaProducer;

    public KafkaRoutingHandler(FosKafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        kafkaProducer.emit(signal);
        return signal;
    }
}
