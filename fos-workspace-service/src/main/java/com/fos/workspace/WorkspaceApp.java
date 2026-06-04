package com.fos.workspace;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletRequest;

@SpringBootApplication(scanBasePackages = {"com.fos.workspace", "com.fos.sdk.events", "com.fos.sdk.security"})
@EnableMongock
@EnableMongoAuditing
@EnableScheduling
public class WorkspaceApp {

    public static void main(String[] args) {
        SpringApplication.run(WorkspaceApp.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    public static class SecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                @Value("${fos.security.enabled:true}") boolean securityEnabled) throws Exception {
            http.csrf(csrf -> csrf.disable());

            if (!securityEnabled) {
                http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
                return http.build();
            }

            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/actuator/health",
                            "/actuator/info",
                            "/api/v1/onlyoffice/callback/**",
                            "/api/v1/onlyoffice/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/onlyoffice/download/**").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/api/v1/onlyoffice/download/**").permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/api/v1/onlyoffice/download/**").permitAll()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(onlyOfficeAwareBearerTokenResolver())
                        .jwt(jwt -> {}));

            return http.build();
        }

        private BearerTokenResolver onlyOfficeAwareBearerTokenResolver() {
            DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
            return request -> shouldBypassResourceServer(request) ? null : delegate.resolve(request);
        }

        private boolean shouldBypassResourceServer(HttpServletRequest request) {
            String path = request.getRequestURI();
            String method = request.getMethod();
            if (path == null || method == null) {
                return false;
            }

            if (path.startsWith("/api/v1/onlyoffice/download/")) {
                return HttpMethod.GET.matches(method)
                        || HttpMethod.HEAD.matches(method)
                        || HttpMethod.OPTIONS.matches(method);
            }

            return path.startsWith("/api/v1/onlyoffice/callback/")
                    || "/api/v1/onlyoffice/health".equals(path);
        }
    }
}
