package com.fos.sdk.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fos.sdk.events.SignalEnvelope;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test utility for asserting Kafka signals without sleep() or polling.
 */
@Component
public class SignalCaptor {

    private final ConsumerFactory<String, String> consumerFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, BlockingQueue<SignalEnvelope>> queues = new ConcurrentHashMap<>();

    public SignalCaptor(ConsumerFactory<String, String> consumerFactory) {
        this.consumerFactory = consumerFactory;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public SignalEnvelope waitForSignal(String topic, long timeoutMillis) {
        BlockingQueue<SignalEnvelope> queue = queues.computeIfAbsent(topic, t -> {
            LinkedBlockingQueue<SignalEnvelope> q = new LinkedBlockingQueue<>();
            startListener(t, q);
            return q;
        });

        try {
            return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void startListener(String topic, BlockingQueue<SignalEnvelope> queue) {
        ContainerProperties props = new ContainerProperties(topic);
        props.setGroupId("fos-test-captor-" + topic.replace(".", "-"));
        props.setMessageListener((MessageListener<String, String>) record -> {
            try {
                SignalEnvelope envelope = objectMapper.readValue(
                        record.value(), SignalEnvelope.class);
                queue.offer(envelope);
            } catch (Exception e) {
                // Ignore
            }
        });

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, props);
        container.start();
    }
}
