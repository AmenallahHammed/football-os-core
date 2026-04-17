package com.fos.sdk.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Remote Proxy to the policy evaluation endpoint in fos-governance-service.
 */
@Component
public class PolicyClient {

    private final RestClient restClient;

    public PolicyClient(
            RestClient.Builder builder,
            @Value("${fos.policy.service-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public PolicyResult evaluate(PolicyRequest request) {
        return restClient.post()
                .uri("/api/v1/policy/evaluate")
                .body(request)
                .retrieve()
                .body(PolicyResult.class);
    }
}
