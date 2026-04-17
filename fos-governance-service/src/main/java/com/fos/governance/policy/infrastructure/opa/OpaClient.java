package com.fos.governance.policy.infrastructure.opa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client for the OPA sidecar running at http://localhost:8181.
 */
@Component
public class OpaClient {

    private final RestClient restClient;

    public OpaClient(RestClient.Builder builder,
                     @Value("${fos.opa.url:http://localhost:8181}") String opaUrl) {
        this.restClient = builder.baseUrl(opaUrl).build();
    }

    /**
     * Sends input to OPA and returns the allow decision.
     */
    public boolean evaluate(Map<String, Object> input) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1/data/fos/allow")
                .body(Map.of("input", input))
                .retrieve()
                .body(Map.class);

        Object result = response != null ? response.get("result") : null;
        return Boolean.TRUE.equals(result);
    }
}
