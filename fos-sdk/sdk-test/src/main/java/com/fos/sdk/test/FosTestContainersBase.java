package com.fos.sdk.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    private static final boolean USING_EXTERNAL_SERVICES;

    static {
        boolean started = false;
        try {
            POSTGRES.start();
            KAFKA.start();
            started = true;
        } catch (Throwable ex) {
            System.err.println("[FosTestContainersBase] Testcontainers unavailable, falling back to external services: " + ex.getMessage());
        }
        USING_EXTERNAL_SERVICES = !started;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (USING_EXTERNAL_SERVICES) {
            registry.add("spring.datasource.url", () ->
                    Optional.ofNullable(System.getenv("POSTGRES_URL"))
                            .orElse("jdbc:postgresql://localhost:5432/fos_governance"));
            registry.add("spring.datasource.username", () ->
                    Optional.ofNullable(System.getenv("POSTGRES_USER")).orElse("fos"));
            registry.add("spring.datasource.password", () ->
                    Optional.ofNullable(System.getenv("POSTGRES_PASSWORD")).orElse("fos"));
            registry.add("spring.kafka.bootstrap-servers", () ->
                    Optional.ofNullable(System.getenv("KAFKA_BOOTSTRAP_SERVERS")).orElse("localhost:9092"));
            registry.add("KEYCLOAK_WEBHOOK_SECRET", () -> "test-keycloak-webhook-secret");
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
