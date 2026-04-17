package com.fos.governance.config;

import com.fos.governance.signal.application.pipeline.*;
import com.fos.governance.signal.domain.port.NotificationPort;
import com.fos.governance.signal.infrastructure.notification.NoopNotificationAdapter;
import com.fos.sdk.events.FosKafkaProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignalPipelineConfig {

    @Bean
    @ConditionalOnMissingBean(NotificationPort.class)
    public NotificationPort notificationPort() {
        return new NoopNotificationAdapter();
    }

    /**
     * Assembles the signal processing Chain of Responsibility.
     */
    @Bean
    public SignalHandler signalPipeline(FosKafkaProducer kafkaProducer,
                                        NotificationPort notificationPort) {
        SchemaValidationHandler head = new SchemaValidationHandler();
        head.then(new ActorEnrichmentHandler())
            .then(new TypeClassificationHandler())
            .then(new KafkaRoutingHandler(kafkaProducer))
            .then(new NotificationFanOutHandler(notificationPort));
        return head;
    }
}
