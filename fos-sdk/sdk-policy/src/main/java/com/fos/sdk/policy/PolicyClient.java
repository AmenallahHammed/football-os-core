package com.fos.sdk.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Remote Proxy to the policy evaluation endpoint in fos-governance-service.
 */
@Component
public class PolicyClient {
    private static final Logger log = LoggerFactory.getLogger(PolicyClient.class);

    private final RestClient restClient;

    public PolicyClient(
            RestClient.Builder builder,
            @Value("${fos.policy.service-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public PolicyResult evaluate(PolicyRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/policy/evaluate")
                    .headers(headers -> resolveAuthorizationHeader()
                            .ifPresent(value -> headers.set(HttpHeaders.AUTHORIZATION, value)))
                    .body(request)
                    .retrieve()
                    .body(PolicyResult.class);
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Governance policy endpoint '/api/v1/policy/evaluate' returned 401 Unauthorized");
            throw ex;
        }
    }

    private Optional<String> resolveAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        String value = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
