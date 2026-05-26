package com.fos.sdk.policy;

import com.fos.sdk.canonical.CanonicalRef;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PolicyClientTest {

    @Test
    void shouldReturnDenyWhenGovernanceRejectsPolicyWithForbidden() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, "http://policy.local");

        server.expect(requestTo("http://policy.local/api/v1/policy/evaluate"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"decision\":\"DENY\",\"reason\":\"Policy denied: workspace.document.contract.read\",\"allowed\":false}"));

        PolicyResult result = client.evaluate(request());

        assertThat(result.decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(result.reason()).isEqualTo("Policy denied: workspace.document.contract.read");
        server.verify();
    }

    @Test
    void shouldStillPropagateUnauthorizedFromGovernance() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PolicyClient client = new PolicyClient(builder, "http://policy.local");

        server.expect(requestTo("http://policy.local/api/v1/policy/evaluate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.evaluate(request()))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        server.verify();
    }

    private PolicyRequest request() {
        UUID clubId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return PolicyRequest.of(
                UUID.fromString("f8cbadea-8eda-46b5-9117-d00ce9148aa2"),
                "ROLE_HEAD_COACH",
                "workspace.document.contract.read",
                CanonicalRef.club(clubId),
                "ACTIVE");
    }
}
