package com.fos.governance.policy;

import com.fos.governance.policy.api.PolicyEvaluationRequest;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.policy.PolicyResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fos.sdk.test.FosTestContainersBase;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyEvaluationIntegrationTest extends FosTestContainersBase {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().port(8181));
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("fos.opa.url", () -> "http://localhost:8181");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return_allow_when_opa_allows() {
        wireMock.stubFor(post(urlEqualTo("/v1/data/fos/allow"))
                .willReturn(okJson("{\"result\": true}")));

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                UUID.randomUUID(), "HEAD_COACH", "workspace.file.read",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()), "ACTIVE", null);

        ResponseEntity<PolicyResult> response = restTemplate.postForEntity(
                "/api/v1/policy/evaluate", request, PolicyResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isAllowed()).isTrue();
    }

    @Test
    void should_return_deny_when_opa_denies() {
        wireMock.stubFor(post(urlEqualTo("/v1/data/fos/allow"))
                .willReturn(okJson("{\"result\": false}")));

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                UUID.randomUUID(), "PLAYER", "workspace.admin.write",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()), "ACTIVE", null);

        ResponseEntity<PolicyResult> response = restTemplate.postForEntity(
                "/api/v1/policy/evaluate", request, PolicyResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isAllowed()).isFalse();
    }
}
