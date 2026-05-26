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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remote Proxy to the policy evaluation endpoint in fos-governance-service.
 */
@Component
public class PolicyClient {
    private static final Logger log = LoggerFactory.getLogger(PolicyClient.class);
    private static final Pattern POLICY_REASON_PATTERN = Pattern.compile("\"reason\"\\s*:\\s*\"([^\"]+)\"");

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
        } catch (HttpClientErrorException.Forbidden ex) {
            log.debug("Governance policy endpoint denied action: {}", ex.getResponseBodyAsString());
            return PolicyResult.deny(resolveDeniedReason(ex));
        }
    }

    private String resolveDeniedReason(HttpClientErrorException.Forbidden ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "Policy denied";
        }

        Matcher matcher = POLICY_REASON_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "Policy denied";
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
