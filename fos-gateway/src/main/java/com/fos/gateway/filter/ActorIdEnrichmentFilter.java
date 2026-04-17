package com.fos.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts actor_id from validated token and injects X-FOS-Actor-Id header.
 */
@Component
public class ActorIdEnrichmentFilter implements GlobalFilter, Ordered {

    public static final String ACTOR_ID_HEADER = "X-FOS-Actor-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(token -> {
                    Jwt jwt = token.getToken();
                    String actorId = jwt.getSubject();
                    ServerHttpRequest enriched = exchange.getRequest().mutate()
                            .header(ACTOR_ID_HEADER, actorId)
                            .build();
                    return exchange.mutate().request(enriched).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
