package com.fos.sdk.test;

import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SignalCaptorTest {

    @Test
    void should_return_signal_when_preloaded_queue_has_value() throws Exception {
        SignalCaptor captor = new SignalCaptor(mock(ConsumerFactory.class));
        String topic = "fos.test.topic";

        SignalEnvelope expected = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic(topic)
                .correlationId("corr-1")
                .timestamp(Instant.now())
                .build();

        BlockingQueue<SignalEnvelope> queue = new LinkedBlockingQueue<>();
        queue.offer(expected);
        queues(captor).put(topic, queue);

        SignalEnvelope received = captor.waitForSignal(topic, 100);

        assertThat(received).isNotNull();
        assertThat(received.signalId()).isEqualTo(expected.signalId());
        assertThat(received.topic()).isEqualTo(topic);
    }

    @Test
    void should_return_null_when_queue_is_empty_until_timeout() throws Exception {
        SignalCaptor captor = new SignalCaptor(mock(ConsumerFactory.class));
        String topic = "fos.test.empty-topic";

        queues(captor).put(topic, new LinkedBlockingQueue<>());

        SignalEnvelope received = captor.waitForSignal(topic, 30);

        assertThat(received).isNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, BlockingQueue<SignalEnvelope>> queues(SignalCaptor captor) throws Exception {
        Field field = SignalCaptor.class.getDeclaredField("queues");
        field.setAccessible(true);
        return (Map<String, BlockingQueue<SignalEnvelope>>) field.get(captor);
    }
}
