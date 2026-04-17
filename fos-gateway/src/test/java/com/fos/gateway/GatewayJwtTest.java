package com.fos.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:${wiremock.server.port}/protocol/openid-connect/certs",
    "fos.governance.url=http://localhost:${wiremock.server.port}"
})
class GatewayJwtTest {

    private static RSAKey rsaKey;
    private static String jwksJson;

    @BeforeAll
    static void generateRsaKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
        jwksJson = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
    }

    @Autowired
    private WebTestClient webTestClient;

    private String generateValidJwt(String actorId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(actorId)
                .issuer("http://localhost/realms/fos")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("preferred_username", "testuser")
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-1").build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    void should_pass_request_with_valid_jwt() throws Exception {
        stubFor(get(urlEqualTo("/protocol/openid-connect/certs"))
                .willReturn(okJson(jwksJson)));

        stubFor(get(urlPathMatching("/api/v1/players/.*"))
                .willReturn(okJson("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Test\"}")));

        String actorId = "actor-uuid-001";
        String jwt = generateValidJwt(actorId);

        webTestClient.get()
                .uri("/api/v1/players/00000000-0000-0000-0000-000000000001")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();

        verify(getRequestedFor(urlPathMatching("/api/v1/players/.*"))
                .withHeader("X-FOS-Actor-Id", equalTo(actorId))
                .withHeader("X-FOS-Request-Id", matching(".+")));
    }

    @Test
    void should_reject_request_without_jwt_with_401() {
        webTestClient.get()
                .uri("/api/v1/players/00000000-0000-0000-0000-000000000001")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_allow_health_endpoint_without_jwt() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
