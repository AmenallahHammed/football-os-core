package com.fos.sdk.test;

import com.fos.sdk.events.KafkaTopics;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

/**
 * Base class for all integration tests.
 * Starts PostgreSQL and Kafka containers once per test class lifecycle.
 */
@Testcontainers
public abstract class FosTestContainersBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("fos_test")
                    .withReuse(true);

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"))
                    .withReuse(true);

    private static final EmbeddedKafkaBroker EMBEDDED_KAFKA;
    private static final boolean USING_EXTERNAL_SERVICES;

    static {
        boolean started = false;
        EmbeddedKafkaBroker embeddedKafka = null;
        try {
            POSTGRES.start();
            KAFKA.start();
            started = true;
        } catch (Throwable ex) {
            System.err.println("[FosTestContainersBase] Testcontainers unavailable, falling back to external services: " + ex.getMessage());
            try {
                embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1,
                        KafkaTopics.AUDIT_ALL,
                        KafkaTopics.IDENTITY_ACTOR_CREATED,
                        KafkaTopics.IDENTITY_ACTOR_UPDATED,
                        KafkaTopics.IDENTITY_ACTOR_DEACTIVATED,
                        KafkaTopics.CANONICAL_PLAYER_CREATED,
                        KafkaTopics.CANONICAL_PLAYER_UPDATED,
                        KafkaTopics.CANONICAL_TEAM_CREATED,
                        KafkaTopics.CANONICAL_TEAM_UPDATED,
                        "fos.test.fact");
                embeddedKafka.afterPropertiesSet();
                System.err.println("[FosTestContainersBase] Embedded Kafka fallback started at "
                        + embeddedKafka.getBrokersAsString());
            } catch (Throwable kafkaEx) {
                System.err.println("[FosTestContainersBase] Embedded Kafka fallback unavailable: " + kafkaEx.getMessage());
            }
        }
        EMBEDDED_KAFKA = embeddedKafka;
        USING_EXTERNAL_SERVICES = !started;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("fos.security.enabled", () -> "false");
        registry.add("fos.identity.keycloak.webhook.secret", () -> "test-keycloak-webhook-secret");

        if (USING_EXTERNAL_SERVICES) {
            registry.add("spring.datasource.url", () ->
                    Optional.ofNullable(System.getenv("POSTGRES_URL"))
                            .orElse("jdbc:postgresql://localhost:5432/fos_governance"));
            registry.add("spring.datasource.username", () ->
                    Optional.ofNullable(System.getenv("POSTGRES_USER")).orElse("fos"));
            registry.add("spring.datasource.password", () ->
                    Optional.ofNullable(System.getenv("POSTGRES_PASSWORD")).orElse("fos"));
            if (EMBEDDED_KAFKA != null) {
                registry.add("spring.kafka.bootstrap-servers", EMBEDDED_KAFKA::getBrokersAsString);
            } else {
                registry.add("spring.kafka.bootstrap-servers", () ->
                        Optional.ofNullable(System.getenv("KAFKA_BOOTSTRAP_SERVERS")).orElse("localhost:9092"));
            }
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
            registry.add("spring.flyway.baseline-version", () -> "0");
            registry.add("spring.flyway.table", () -> "flyway_schema_history_test");
            return;
        }

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
