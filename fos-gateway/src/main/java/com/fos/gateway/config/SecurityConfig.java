package com.fos.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Configures JWT validation for the gateway.
 * All routes require a valid Keycloak JWT except narrowly-scoped public
 * endpoints such as health, OnlyOffice callback, and tokenized downloads.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final ServerBearerTokenAuthenticationConverter BEARER_TOKEN_CONVERTER =
            new ServerBearerTokenAuthenticationConverter();

    @Value("${fos.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        if (!securityEnabled) {
            return http.cors(cors -> {})
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(ex -> ex.anyExchange().permitAll())
                    .build();
        }

        http.cors(cors -> {})
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Preflight CORS requests must remain public; browsers do not
                // attach user JWTs to OPTIONS checks.
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/onlyoffice/download/**").permitAll()
                .pathMatchers(HttpMethod.HEAD, "/api/v1/onlyoffice/download/**").permitAll()
                .pathMatchers(HttpMethod.OPTIONS, "/api/v1/onlyoffice/download/**").permitAll()
                .pathMatchers(
                    "/actuator/health",
                    "/api/v1/onlyoffice/callback/**",
                    "/api/v1/onlyoffice/health").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenConverter(exchange -> shouldBypassResourceServer(exchange.getRequest())
                        ? Mono.empty()
                        : BEARER_TOKEN_CONVERTER.convert(exchange))
                .jwt(jwt -> {})
            );

        return http.build();
    }

    private boolean shouldBypassResourceServer(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        if (path == null || method == null) {
            return false;
        }

        if (path.startsWith("/api/v1/onlyoffice/download/")) {
            return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
        }

        return path.startsWith("/api/v1/onlyoffice/callback/")
                || "/api/v1/onlyoffice/health".equals(path);
    }
}
