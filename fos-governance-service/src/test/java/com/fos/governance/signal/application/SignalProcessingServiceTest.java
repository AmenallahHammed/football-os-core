package com.fos.governance.signal.application;

import com.fos.governance.signal.application.pipeline.*;
import com.fos.governance.signal.domain.port.NotificationPort;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SignalProcessingServiceTest {

    private SignalProcessingService service;
    private FosKafkaProducer kafkaProducer;
    private NotificationPort notificationPort;

    @BeforeEach
    void setUp() {
        kafkaProducer = mock(FosKafkaProducer.class);
        notificationPort = mock(NotificationPort.class);

        // Manual chain assembly for test
        SignalHandler head = new SchemaValidationHandler();
        head.then(new ActorEnrichmentHandler())
            .then(new TypeClassificationHandler())
            .then(new KafkaRoutingHandler(kafkaProducer))
            .then(new NotificationFanOutHandler(notificationPort));

        service = new SignalProcessingService(head);
    }

    @Test
    void should_reject_signal_missing_actor() {
        SignalEnvelope signal = SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("test")
                .actorRef(null) // Rejection trigger
                .build();

        SignalEnvelope result = service.process(signal);
        assertNull(result);
        verifyNoInteractions(kafkaProducer);
    }

    @Test
    void should_process_valid_signal() {
        SignalEnvelope signal = SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("test.topic")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()).toString())
                .build();

        SignalEnvelope result = service.process(signal);
        assertNotNull(result);
        verify(kafkaProducer, times(1)).emit(any());
    }

    @Test
    void should_fan_out_alert_signals() {
        SignalEnvelope signal = SignalEnvelope.builder()
                .type(SignalType.ALERT)
                .topic("test.alert")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()).toString())
                .build();

        service.process(signal);
        verify(notificationPort, times(1)).sendAlert(any());
    }
}
