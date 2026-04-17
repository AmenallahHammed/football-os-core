package com.fos.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centrally defined routing for the FOS ecosystem.
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${fos.governance.url:http://localhost:8081}")
    private String governanceUrl;

    @Value("${fos.workspace.url:http://localhost:8082}")
    private String workspaceUrl;

    @Bean
    public RouteLocator fosRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("fos-governance", r -> r
                        .path("/api/v1/actors/**",
                              "/api/v1/players/**",
                              "/api/v1/teams/**",
                              "/api/v1/policy/**",
                              "/api/v1/signals/**",
                              "/api/v1/identity/**")
                        .uri(governanceUrl))
                .route("fos-workspace", r -> r
                        .path("/api/v1/documents/**",
                              "/api/v1/events/**",
                              "/api/v1/profiles/**",
                              "/api/v1/notifications/**",
                              "/api/v1/search/**")
                        .uri(workspaceUrl))
                .build();
    }
}
