package com.fos.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    "spring.cloud.gateway.routes[0].id=test-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:${wiremock.server.port}",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/test/**",
    "fos.security.enabled=false"
})
class CorrelationIdFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_inject_x_fos_request_id_header_when_missing() {
        stubFor(get(anyUrl()).willReturn(ok()));

        WebTestClient.ResponseSpec response = webTestClient.get()
                .uri("/test/resource")
                .exchange();

        verify(getRequestedFor(anyUrl())
                .withHeader("X-FOS-Request-Id", matching(".+")));
    }

    @Test
    void should_preserve_x_fos_request_id_when_already_present() {
        stubFor(get(anyUrl()).willReturn(ok()));

        webTestClient.get()
                .uri("/test/resource")
                .header("X-FOS-Request-Id", "client-provided-id-001")
                .exchange();

        verify(getRequestedFor(anyUrl())
                .withHeader("X-FOS-Request-Id", equalTo("client-provided-id-001")));
    }
}
