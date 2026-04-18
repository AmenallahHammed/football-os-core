package com.fos.workspace;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootApplication(scanBasePackages = {"com.fos.workspace", "com.fos.sdk.events", "com.fos.sdk.security"})
@EnableMongock
@EnableMongoAuditing
@EnableScheduling
public class WorkspaceApp {

    public static void main(String[] args) {
        SpringApplication.run(WorkspaceApp.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    static class SecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                @Value("${fos.security.enabled:true}") boolean securityEnabled) throws Exception {
            http.csrf(csrf -> csrf.disable());

            if (!securityEnabled) {
                http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
                return http.build();
            }

            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

            return http.build();
        }
    }
}
