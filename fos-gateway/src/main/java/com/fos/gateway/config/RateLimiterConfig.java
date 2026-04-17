package com.fos.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Redis-backed rate limiter keyed by actor ID.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver actorKeyResolver() {
        return exchange -> {
            String actorId = exchange.getRequest().getHeaders().getFirst("X-FOS-Actor-Id");
            if (actorId != null && !actorId.isBlank()) {
                return Mono.just(actorId);
            }
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(remoteAddr);
        };
    }
}
