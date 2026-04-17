# Phase 0 Sprint 0.5 — fos-gateway + SDK Client End-to-End Wiring

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `fos-gateway` is running as a Spring Cloud Gateway on port 8080. It validates Keycloak JWTs, injects `X-FOS-Request-Id` on every request, and enforces Redis rate limiting (100 req/min per actor). `PolicyClient` and `CanonicalServiceClient` from the SDK are wired in a minimal sample domain service (or directly in a governance test harness) to verify end-to-end: JWT passes gateway → `PolicyClient` resolves → `CanonicalServiceClient` resolves. Integration tests confirm: valid JWT → 200; missing JWT → 401; rate limit exceeded → 429.

**Architecture:** `fos-gateway` is a standalone Spring Boot project using Spring Cloud Gateway. It acts as the only entry point for external traffic — no service is directly reachable except via the gateway in production. JWT validation uses the Keycloak JWKS endpoint; no local secret needed. Redis backs the rate limiter keyed by JWT `sub` (actor ID). Downstream services see enriched headers: `X-FOS-Request-Id` and `X-FOS-Actor-Id`.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Cloud Gateway, Spring Security OAuth2 Resource Server, Redis 7.2, Keycloak 24 (JWKS endpoint), JUnit 5, Testcontainers (Keycloak + Redis), WireMock

**Required Patterns This Sprint:**
- `[REQUIRED]` **Proxy** — The gateway is a Proxy: it intercepts all HTTP traffic and applies cross-cutting concerns before routing downstream
- No new patterns from DESIGN-PATTERNS.md apply; this sprint is infrastructure wiring

---

## File Map

```
fos-gateway/
├── pom.xml                                                     CREATE
└── src/
    ├── main/
    │   ├── java/com/fos/gateway/
    │   │   ├── GatewayApp.java                                 CREATE
    │   │   ├── filter/
    │   │   │   └── CorrelationIdFilter.java                    CREATE
    │   │   └── config/
    │   │       ├── GatewayRoutesConfig.java                    CREATE
    │   │       ├── RateLimiterConfig.java                      CREATE
    │   │       └── SecurityConfig.java                         CREATE
    │   └── resources/
    │       └── application.yml                                 CREATE
    └── test/java/com/fos/gateway/
        ├── GatewayJwtTest.java                                 CREATE
        └── GatewayRateLimitTest.java                           CREATE
```

---

## Task 1: fos-gateway — Project Scaffold

**Files:**
- Create: `fos-gateway/pom.xml`
- Create: `fos-gateway/src/main/java/com/fos/gateway/GatewayApp.java`

- [ ] **Step 1: Create fos-gateway pom.xml**

```xml
<!-- fos-gateway/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.fos</groupId>
    <artifactId>football-os-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>

  <artifactId>fos-gateway</artifactId>
  <name>FOS Gateway</name>
  <description>Spring Cloud Gateway — JWT validation, correlation ID, Redis rate limiting</description>

  <dependencies>
    <!-- Spring Cloud Gateway (reactive) -->
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>

    <!-- JWT validation via Keycloak JWKS -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- Redis for rate limiting -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>

    <!-- Actuator for health check -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>1.19.8</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.wiremock</groupId>
      <artifactId>wiremock-standalone</artifactId>
      <version>3.5.4</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>2023.0.3</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create GatewayApp**

```java
// GatewayApp.java
package com.fos.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApp {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }
}
```

- [ ] **Step 3: Add fos-gateway to monorepo parent pom.xml**

In `football-os-core/pom.xml`, add `<module>fos-gateway</module>` to the modules section:

```xml
<modules>
  <module>fos-sdk</module>
  <module>fos-governance-service</module>
  <module>fos-gateway</module>   <!-- add this line -->
</modules>
```

- [ ] **Step 4: Commit**

```bash
git add fos-gateway/ football-os-core/pom.xml
git commit -m "feat(gateway): scaffold fos-gateway Spring Cloud Gateway project"
```

---

## Task 2: fos-gateway — CorrelationIdFilter

**Files:**
- Create: `fos-gateway/src/main/java/com/fos/gateway/filter/CorrelationIdFilter.java`

- [ ] **Step 1: Write the failing test**

```java
// fos-gateway/src/test/java/com/fos/gateway/CorrelationIdFilterTest.java
package com.fos.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    "spring.cloud.gateway.routes[0].id=test-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:${wiremock.server.port}",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/test/**",
    "fos.security.enabled=false"   // disable JWT for this filter-only test
})
class CorrelationIdFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_inject_x_fos_request_id_header_when_missing() {
        stubFor(get(anyUrl()).willReturn(ok()));

        WebTestClient.ResponseSpec response = webTestClient.get()
                .uri("/test/resource")
                .exchange();

        // The downstream WireMock received the enriched request
        verify(getRequestedFor(anyUrl())
                .withHeader("X-FOS-Request-Id", matching(".+")));
    }

    @Test
    void should_preserve_x_fos_request_id_when_already_present() {
        stubFor(get(anyUrl()).willReturn(ok()));

        webTestClient.get()
                .uri("/test/resource")
                .header("X-FOS-Request-Id", "client-provided-id-001")
                .exchange();

        verify(getRequestedFor(anyUrl())
                .withHeader("X-FOS-Request-Id", equalTo("client-provided-id-001")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-gateway
mvn test -Dtest=CorrelationIdFilterTest -q
```

Expected: FAIL — `CorrelationIdFilter` bean missing or filter not applied

- [ ] **Step 3: Implement CorrelationIdFilter**

```java
// CorrelationIdFilter.java
package com.fos.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that ensures every request carries an X-FOS-Request-Id header.
 * If the header is absent, a new UUID is generated.
 * Downstream services receive this header and attach it to Kafka signals for tracing.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String CORRELATION_HEADER = "X-FOS-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String correlationId = request.getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated correlation ID: {}", correlationId);
        }

        final String finalCorrelationId = correlationId;
        ServerHttpRequest enriched = request.mutate()
                .header(CORRELATION_HEADER, finalCorrelationId)
                .build();

        return chain.filter(exchange.mutate().request(enriched).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // runs before JWT validation and rate limiting
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-gateway
mvn test -Dtest=CorrelationIdFilterTest -q
```

Expected: BUILD SUCCESS — 2 tests pass

- [ ] **Step 5: Commit**

```bash
git add fos-gateway/src/
git commit -m "feat(gateway): add CorrelationIdFilter — inject X-FOS-Request-Id on every request"
```

---

## Task 3: fos-gateway — Security Config (JWT Validation via Keycloak JWKS)

**Files:**
- Create: `fos-gateway/src/main/java/com/fos/gateway/config/SecurityConfig.java`
- Create: `fos-gateway/src/main/java/com/fos/gateway/filter/ActorIdEnrichmentFilter.java`

- [ ] **Step 1: Implement SecurityConfig**

```java
// SecurityConfig.java
package com.fos.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures JWT validation for the gateway.
 * All routes require a valid Keycloak JWT except the actuator health endpoint.
 *
 * JWT verification uses the Keycloak JWKS endpoint — no secret keys needed locally.
 * JWKS URI: ${fos.keycloak.jwks-uri} — set to the Keycloak realm JWKS endpoint.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${fos.security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        if (!securityEnabled) {
            // Used only in filter-only tests — disable entirely
            return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(ex -> ex.anyExchange().permitAll())
                    .build();
        }

        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {}) // JWKS URI auto-configured from spring.security.oauth2.resourceserver.jwt.jwk-set-uri
            );

        return http.build();
    }
}
```

- [ ] **Step 2: Create ActorIdEnrichmentFilter — propagates actor ID downstream**

```java
// ActorIdEnrichmentFilter.java
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
 * Extracts the actor_id (JWT sub claim) from the validated token
 * and propagates it as X-FOS-Actor-Id header to downstream services.
 * Downstream services use FosSecurityContext.getActorId() — which reads this header.
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
        return Ordered.HIGHEST_PRECEDENCE + 10; // after CorrelationIdFilter
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add fos-gateway/src/main/java/com/fos/gateway/
git commit -m "feat(gateway): add SecurityConfig for Keycloak JWT validation, ActorIdEnrichmentFilter"
```

---

## Task 4: fos-gateway — Redis Rate Limiter

**Files:**
- Create: `fos-gateway/src/main/java/com/fos/gateway/config/RateLimiterConfig.java`

- [ ] **Step 1: Write the failing test**

```java
// GatewayRateLimitTest.java
package com.fos.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    "spring.cloud.gateway.routes[0].id=test-route",
    "spring.cloud.gateway.routes[0].uri=http://localhost:${wiremock.server.port}",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/**",
    "spring.cloud.gateway.routes[0].filters[0]=name=RequestRateLimiter",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.replenishRate=1",
    "spring.cloud.gateway.routes[0].filters[0].args.redis-rate-limiter.burstCapacity=1",
    "spring.cloud.gateway.routes[0].filters[0].args.key-resolver=#{@actorKeyResolver}",
    "fos.security.enabled=false"
})
class GatewayRateLimitTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_return_429_when_rate_limit_exceeded() {
        stubFor(get(anyUrl()).willReturn(ok()));

        // First request passes (rate limit: 1 per burst)
        webTestClient.get().uri("/api/test")
                .header("X-FOS-Actor-Id", "test-actor-rate-limit")
                .exchange()
                .expectStatus().isOk();

        // Second request immediately after should be rate-limited
        webTestClient.get().uri("/api/test")
                .header("X-FOS-Actor-Id", "test-actor-rate-limit")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-gateway
mvn test -Dtest=GatewayRateLimitTest -q
```

Expected: FAIL — `actorKeyResolver` bean missing or rate limiter not wired

- [ ] **Step 3: Implement RateLimiterConfig**

```java
// RateLimiterConfig.java
package com.fos.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Redis-backed rate limiter keyed by actor ID (JWT sub).
 * Each authenticated actor gets 100 requests/minute (replenishRate).
 * Burst capacity of 120 allows brief spikes.
 *
 * Key: X-FOS-Actor-Id header (set by ActorIdEnrichmentFilter after JWT validation).
 * Falls back to remote address for unauthenticated requests reaching the rate limiter.
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
            // Fallback: use remote address (for health checks and unauthenticated probes)
            String remoteAddr = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(remoteAddr);
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-gateway
mvn test -Dtest=GatewayRateLimitTest -q
```

Expected: BUILD SUCCESS — 1 test passes

- [ ] **Step 5: Commit**

```bash
git add fos-gateway/src/main/java/com/fos/gateway/config/RateLimiterConfig.java \
        fos-gateway/src/test/java/com/fos/gateway/GatewayRateLimitTest.java
git commit -m "feat(gateway): add Redis rate limiter keyed by actor ID (100 req/min)"
```

---

## Task 5: fos-gateway — Route Configuration + application.yml

**Files:**
- Create: `fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java`
- Create: `fos-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Create application.yml**

```yaml
# fos-gateway/src/main/resources/application.yml

server:
  port: 8080

spring:
  application:
    name: fos-gateway

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8090/realms/fos/protocol/openid-connect/certs}

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter:
              replenishRate: 100
              burstCapacity: 120
            key-resolver: "#{@actorKeyResolver}"

management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: never

fos:
  security:
    enabled: ${FOS_SECURITY_ENABLED:true}
  governance:
    url: ${GOVERNANCE_URL:http://localhost:8081}
```

- [ ] **Step 2: Create GatewayRoutesConfig**

```java
// GatewayRoutesConfig.java
package com.fos.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All gateway routes are defined here — one source of truth.
 * Domain services are added as new routes when each domain repo is created.
 *
 * Phase 0 routes: governance only.
 * Phase 1+: workspace, dataperF, etc. are added as they come online.
 */
@Configuration
public class GatewayRoutesConfig {

    @Value("${fos.governance.url:http://localhost:8081}")
    private String governanceUrl;

    @Bean
    public RouteLocator fosRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Governance service — identity, canonical, policy, signal endpoints
                .route("fos-governance", r -> r
                        .path("/api/v1/actors/**",
                              "/api/v1/players/**",
                              "/api/v1/teams/**",
                              "/api/v1/policy/**",
                              "/api/v1/signals/**",
                              "/api/v1/identity/**")
                        .uri(governanceUrl))
                .build();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add fos-gateway/src/main/resources/ \
        fos-gateway/src/main/java/com/fos/gateway/config/GatewayRoutesConfig.java
git commit -m "feat(gateway): add route configuration and application.yml with Keycloak JWKS, Redis rate limiter"
```

---

## Task 6: JWT Integration Tests

**Files:**
- Create: `fos-gateway/src/test/java/com/fos/gateway/GatewayJwtTest.java`

- [ ] **Step 1: Write GatewayJwtTest**

```java
// GatewayJwtTest.java
package com.fos.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Tests JWT validation at the gateway layer.
 * WireMock simulates both the Keycloak JWKS endpoint and the downstream governance service.
 * No real Keycloak or governance service needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
    // JWKS endpoint points to WireMock
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:${wiremock.server.port}/protocol/openid-connect/certs",
    // Downstream governance points to WireMock
    "fos.governance.url=http://localhost:${wiremock.server.port}"
})
class GatewayJwtTest {

    private static RSAKey rsaKey;
    private static String jwksJson;

    @BeforeAll
    static void generateRsaKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();
        // Build JWKS JSON that Spring Security expects
        jwksJson = """
            {"keys":[%s]}
            """.formatted(rsaKey.toPublicJWK().toJSONString());
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // No extra properties needed — WireMock port is injected via @TestPropertySource
    }

    @Autowired
    private WebTestClient webTestClient;

    private String generateValidJwt(String actorId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(actorId)
                .issuer("http://localhost/realms/fos")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("preferred_username", "testuser")
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-1").build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    void should_pass_request_with_valid_jwt() throws Exception {
        // WireMock serves JWKS
        stubFor(get(urlEqualTo("/protocol/openid-connect/certs"))
                .willReturn(okJson(jwksJson)));

        // WireMock acts as downstream governance
        stubFor(get(urlPathMatching("/api/v1/players/.*"))
                .willReturn(okJson("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Test\"}")));

        String jwt = generateValidJwt("actor-uuid-001");

        webTestClient.get()
                .uri("/api/v1/players/00000000-0000-0000-0000-000000000001")
                .header("Authorization", "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void should_reject_request_without_jwt_with_401() {
        webTestClient.get()
                .uri("/api/v1/players/00000000-0000-0000-0000-000000000001")
                // No Authorization header
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_allow_health_endpoint_without_jwt() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
```

- [ ] **Step 2: Add Nimbus JOSE test dependency to pom.xml**

In `fos-gateway/pom.xml`, add:

```xml
<dependency>
  <groupId>com.nimbusds</groupId>
  <artifactId>nimbus-jose-jwt</artifactId>
  <scope>test</scope>
  <!-- version managed by spring-boot-dependencies BOM -->
</dependency>
```

- [ ] **Step 3: Run JWT tests**

```bash
cd fos-gateway
mvn test -Dtest=GatewayJwtTest -q
```

Expected: BUILD SUCCESS — 3 tests pass

- [ ] **Step 4: Commit**

```bash
git add fos-gateway/src/test/java/com/fos/gateway/GatewayJwtTest.java \
        fos-gateway/pom.xml
git commit -m "test(gateway): add JWT validation tests (valid JWT → 200, missing JWT → 401, health → 200)"
```

---

## Task 7: sdk-canonical + sdk-policy End-to-End Wiring Verification

This task verifies that `PolicyClient` and `CanonicalServiceClient` from the SDK work correctly when wired together. No new service code is written — this is a wiring test using WireMock to simulate the governance service.

**Files:**
- Create: `fos-governance-service/src/test/java/com/fos/governance/SdkClientWiringTest.java`

- [ ] **Step 1: Write SdkClientWiringTest**

```java
// SdkClientWiringTest.java
package com.fos.governance;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalResolver;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyDecision;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that sdk-canonical and sdk-policy client beans are wired correctly.
 * WireMock simulates the governance HTTP endpoints that the SDK clients call.
 * This test proves the SDK client → HTTP → endpoint chain works end-to-end.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SdkClientWiringTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")).withDatabaseName("fos_governance");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Point SDK clients to WireMock (simulates governance service)
        registry.add("fos.canonical.service-url", () -> "http://localhost:" + wireMock.port());
        registry.add("fos.policy.service-url",    () -> "http://localhost:" + wireMock.port());
        registry.add("fos.opa.url",               () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    private CanonicalResolver canonicalResolver;

    @Autowired
    private PolicyClient policyClient;

    @Test
    void should_resolve_player_via_canonical_resolver() {
        UUID playerId = UUID.randomUUID();
        wireMock.stubFor(get(urlEqualTo("/api/v1/players/" + playerId))
                .willReturn(okJson("""
                    {"id":"%s","name":"SDK Test Player","position":"CF",
                     "nationality":"DE","dateOfBirth":"1993-07-15","currentTeamId":null}
                    """.formatted(playerId))));

        PlayerDTO player = canonicalResolver.getPlayer(playerId);

        assertThat(player.name()).isEqualTo("SDK Test Player");
        assertThat(player.position()).isEqualTo("CF");
    }

    @Test
    void should_evaluate_policy_via_policy_client() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));

        PolicyRequest request = PolicyRequest.of(
                UUID.randomUUID(), "HEAD_COACH", "workspace.file.read",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()), "ACTIVE");

        PolicyResult result = policyClient.evaluate(request);

        assertThat(result.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(result.isAllowed()).isTrue();
    }
}
```

- [ ] **Step 2: Run the wiring test**

```bash
cd fos-governance-service
mvn test -Dtest=SdkClientWiringTest -q
```

Expected: BUILD SUCCESS — 2 tests pass

- [ ] **Step 3: Commit**

```bash
git add fos-governance-service/src/test/java/com/fos/governance/SdkClientWiringTest.java
git commit -m "test(governance): add SDK client wiring test — CanonicalResolver and PolicyClient against WireMock"
```

---

## Task 8: Full Build Verification

- [ ] **Step 1: Build entire monorepo**

```bash
cd football-os-core
mvn package -q
```

Expected: BUILD SUCCESS for all modules: fos-sdk (7 modules), fos-governance-service, fos-gateway

- [ ] **Step 2: Start full stack with docker-compose**

Ensure `docker-compose.yml` in `football-os-core` includes Redis (it was added in Sprint 0.1 Amendment A). If missing, add:

```yaml
  redis:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
```

```bash
cd football-os-core
docker-compose up -d
```

Expected: All containers healthy — keycloak, postgres, kafka, redis, minio

- [ ] **Step 3: Start governance service and gateway**

```bash
# Terminal 1 — governance
java -jar fos-governance-service/target/fos-governance-service-*.jar &

# Terminal 2 — gateway
java -jar fos-gateway/target/fos-gateway-*.jar &
```

Expected: Both services start without errors.

- [ ] **Step 4: Verify health endpoints**

```bash
curl -s http://localhost:8080/actuator/health | jq .
# Expected: {"status":"UP"}

curl -s http://localhost:8081/actuator/health | jq .
# Expected: {"status":"UP"}
```

- [ ] **Step 5: Verify 401 without token**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/players/
# Expected: 401
```

- [ ] **Step 6: Final commit**

```bash
git add .
git commit -m "chore(gateway): sprint 0.6 complete — gateway JWT validation, rate limiting, correlation ID, SDK clients verified"
```

---

## Sprint Test Criteria

Sprint 0.6 is complete when:

1. `mvn test` passes in `fos-gateway` (5+ tests: CorrelationIdFilter + JWT + rate limit)
2. `mvn test` passes in `fos-governance-service` (2 additional tests: SdkClientWiringTest)
3. `GET /actuator/health` on gateway (port 8080) → 200 without any token
4. `GET /api/v1/players/{id}` on gateway without JWT → 401
5. `GET /api/v1/players/{id}` on gateway with valid Keycloak JWT → passes through to governance → 200
6. Redis rate limiter returns 429 after 100 requests/minute per actor
7. Every request passing through the gateway has `X-FOS-Request-Id` header visible to downstream services
8. `CanonicalResolver.getPlayer()` resolves via HTTP — verified in `SdkClientWiringTest`
9. `PolicyClient.evaluate()` sends request and gets result — verified in `SdkClientWiringTest`

---

## What NOT to Include in This Sprint

- **HTTPS / TLS termination** — handled at the load balancer level in production; not in the gateway in dev
- **Multi-tenant Keycloak realms** — single `fos` realm for all actors; multi-tenancy deferred
- **Circuit breaker / fallback** — added when a domain service actually has reliability requirements
- **Distributed tracing (Zipkin, Jaeger)** — not in Phase 0
- **Per-route rate limits** — global 100 req/min is sufficient in Phase 0; per-route tuning in Phase 1+
- **sdk-test (FosTestContainersBase)** — built in Sprint 0.6; tests in this sprint use raw Testcontainers

---

## SDK Dependencies Used

| Module | Usage |
|--------|-------|
| `sdk-canonical` | `CanonicalResolver`, `CanonicalServiceClient` — verified in wiring test |
| `sdk-policy` | `PolicyClient` — verified in wiring test |
| `sdk-security` | `FosSecurityContext` — used by `PolicyGuardAspect`; gateway propagates `X-FOS-Actor-Id` |

---

## Kafka Topics

No new topics produced or consumed in this sprint. The gateway does not interact with Kafka directly.
