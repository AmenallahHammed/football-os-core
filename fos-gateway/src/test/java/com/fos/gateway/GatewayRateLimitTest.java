package com.fos.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    "spring.cloud.gateway.routes[0].id=test-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:${wiremock.server.port}",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/**",
    "spring.cloud.gateway.routes[0].filters[0]=name=RequestRateLimiter",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.replenishRate=1",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.burstCapacity=1",
    "spring.cloud.gateway.routes[0].filters[0].args.key-resolver=#{@actorKeyResolver}",
    "fos.security.enabled=false"
})
class GatewayRateLimitTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_return_429_when_rate_limit_exceeded() {
        stubFor(get(anyUrl()).willReturn(ok()));

        // First request passes
        webTestClient.get().uri("/api/test")
                .header("X-FOS-Actor-Id", "test-actor-rate-limit")
                .exchange()
                .expectStatus().isOk();

        // Second request immediately after
        webTestClient.get().uri("/api/test")
                .header("X-FOS-Actor-Id", "test-actor-rate-limit")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
