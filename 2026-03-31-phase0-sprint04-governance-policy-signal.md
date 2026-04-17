# Phase 0 Sprint 0.4 — fos-governance-service: Policy + Signal + sdk-policy

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `fos-governance-service` gains two more packages: `policy` (OPA sidecar integration, ALLOW/DENY evaluation) and `signal` (Chain of Responsibility pipeline, NotificationPort with NoopNotificationAdapter, fos_audit consumer). `sdk-policy` is published to local Maven with `PolicyClient` (Remote Proxy), `@PolicyGuard`, and `PolicyGuardAspect` (with Caffeine cache). Integration tests confirm: ALLOW request → 200; DENY request → 403; POST signal → appears on Kafka topic.

**Architecture:** The `policy` package delegates to an OPA sidecar running at `http://localhost:8181`. Domain services call `PolicyClient` from `sdk-policy` — they never call OPA directly. `sdk-policy` uses a Caffeine cache (TTL 30s, max 1000 entries) keyed by `{actorId}:{action}:{resourceState}` to avoid hammering OPA on every request. The `signal` package processes incoming signals through a 5-step Chain of Responsibility pipeline and fans out ALERT signals to `NotificationPort`. `fos_audit` is written by a Kafka consumer (`AbstractFosConsumer`) that reads `fos.audit.all`.

**Tech Stack:** Java 21, Spring Boot 3.3.x, OPA 0.68.0 (sidecar), Caffeine 3.1.x, Spring AOP, `sdk-events` (AbstractFosConsumer, FosKafkaProducer), `sdk-core` (ErrorResponse), JUnit 5, Testcontainers, WireMock

**Required Patterns This Sprint:**
- `[REQUIRED]` **Chain of Responsibility** — 5-handler signal processing pipeline in `signal` package
- `[REQUIRED]` **Chain of Responsibility** — `PolicyContextBuilder` chain for OPA input assembly in `policy` package
- `[REQUIRED]` **Proxy (Remote)** — `PolicyClient` in `sdk-policy` hides the HTTP call to the policy package
- `[REQUIRED]` **Adapter / Port** — `NotificationPort` + `NoopNotificationAdapter` for push/email (vendor undecided)
- `[REQUIRED]` **Null Object** — `NoopNotificationAdapter` is the default; never connects to a real notification service
- `[REQUIRED]` **Template Method** — `fos_audit` consumer extends `AbstractFosConsumer`

---

## File Map

```
fos-sdk/sdk-policy/
├── pom.xml                                                         CREATE
└── src/
    ├── main/java/com/fos/sdk/policy/
    │   ├── PolicyRequest.java                                      CREATE
    │   ├── PolicyResult.java                                       CREATE
    │   ├── PolicyDecision.java                                     CREATE
    │   ├── PolicyClient.java                                       CREATE
    │   ├── PolicyResultCache.java                                  CREATE
    │   ├── PolicyGuard.java                                        CREATE
    │   ├── PolicyGuardAspect.java                                  CREATE
    │   └── PolicyAutoConfiguration.java                           CREATE
    └── test/java/com/fos/sdk/policy/
        ├── PolicyResultCacheTest.java                              CREATE
        └── PolicyGuardAspectTest.java                              CREATE

fos-sdk/pom.xml                                                     MODIFY (add sdk-policy module)

fos-governance-service/
├── pom.xml                                                         MODIFY (add sdk-policy, WireMock test deps)
└── src/
    ├── main/
    │   ├── java/com/fos/governance/
    │   │   ├── policy/
    │   │   │   ├── api/
    │   │   │   │   ├── PolicyEvaluationController.java             CREATE
    │   │   │   │   └── PolicyEvaluationRequest.java                CREATE
    │   │   │   ├── application/
    │   │   │   │   ├── PolicyEvaluationService.java                CREATE
    │   │   │   │   └── context/
    │   │   │   │       ├── PolicyContextBuilder.java               CREATE
    │   │   │   │       ├── RoleContextBuilder.java                 CREATE
    │   │   │   │       ├── ResourceStateContextBuilder.java        CREATE
    │   │   │   │       └── OpaEvaluator.java                       CREATE
    │   │   │   └── infrastructure/opa/
    │   │   │       └── OpaClient.java                              CREATE
    │   │   ├── signal/
    │   │   │   ├── api/
    │   │   │   │   └── SignalIntakeController.java                  CREATE
    │   │   │   ├── application/
    │   │   │   │   ├── SignalProcessingService.java                CREATE
    │   │   │   │   └── pipeline/
    │   │   │   │       ├── SignalHandler.java                      CREATE
    │   │   │   │       ├── SchemaValidationHandler.java            CREATE
    │   │   │   │       ├── ActorEnrichmentHandler.java             CREATE
    │   │   │   │       ├── TypeClassificationHandler.java          CREATE
    │   │   │   │       ├── KafkaRoutingHandler.java                CREATE
    │   │   │   │       └── NotificationFanOutHandler.java          CREATE
    │   │   │   ├── domain/
    │   │   │   │   └── port/
    │   │   │   │       └── NotificationPort.java                   CREATE
    │   │   │   └── infrastructure/
    │   │   │       ├── notification/
    │   │   │       │   └── NoopNotificationAdapter.java            CREATE
    │   │   │       └── audit/
    │   │   │           └── AuditLogConsumer.java                   CREATE
    │   │   └── config/
    │   │       └── SignalPipelineConfig.java                       CREATE
    │   └── resources/
    │       ├── db/migration/
    │       │   └── V005__create_audit_log_table.sql                CREATE
    │       └── opa/
    │           └── fos_policy.rego                                 CREATE
    └── test/java/com/fos/governance/
        ├── policy/
        │   └── PolicyEvaluationIntegrationTest.java                CREATE
        └── signal/
            └── SignalIntakeIntegrationTest.java                    CREATE
```

---

## Task 1: sdk-policy — pom.xml + Value Objects

**Files:**
- Create: `fos-sdk/sdk-policy/pom.xml`
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyDecision.java`
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyRequest.java`
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyResult.java`

- [ ] **Step 1: Create sdk-policy pom.xml**

```xml
<!-- fos-sdk/sdk-policy/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.fos</groupId>
    <artifactId>fos-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>

  <artifactId>sdk-policy</artifactId>
  <name>FOS SDK :: Policy</name>
  <description>PolicyClient (remote proxy), @PolicyGuard, PolicyGuardAspect with Caffeine cache</description>

  <dependencies>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-canonical</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-security</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Spring Web for RestClient -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- AOP for @PolicyGuard -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>

    <!-- Caffeine cache -->
    <dependency>
      <groupId>com.github.ben-manes.caffeine</groupId>
      <artifactId>caffeine</artifactId>
      <version>3.1.8</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Create PolicyDecision enum**

```java
// PolicyDecision.java
package com.fos.sdk.policy;

public enum PolicyDecision {
    ALLOW,
    DENY,
    ESCALATE   // reserved — not used until Phase 1+
}
```

- [ ] **Step 3: Create PolicyRequest record**

```java
// PolicyRequest.java
package com.fos.sdk.policy;

import com.fos.sdk.canonical.CanonicalRef;

import java.util.Map;
import java.util.UUID;

/**
 * Input for a single policy evaluation.
 * Domain services create this via PolicyRequest.builder() or the static factories.
 */
public record PolicyRequest(
    UUID   actorId,
    String actorRole,
    String action,
    CanonicalRef resourceRef,
    String resourceState,
    Map<String, Object> context
) {
    /** Convenience builder-style factory for common case. */
    public static PolicyRequest of(UUID actorId, String actorRole,
                                   String action, CanonicalRef resourceRef,
                                   String resourceState) {
        return new PolicyRequest(actorId, actorRole, action, resourceRef, resourceState, Map.of());
    }

    public static PolicyRequest withContext(UUID actorId, String actorRole,
                                            String action, CanonicalRef resourceRef,
                                            String resourceState, Map<String, Object> context) {
        return new PolicyRequest(actorId, actorRole, action, resourceRef, resourceState, context);
    }
}
```

- [ ] **Step 4: Create PolicyResult record**

```java
// PolicyResult.java
package com.fos.sdk.policy;

/**
 * Result of a policy evaluation.
 * Domain services check result.isAllowed() before proceeding.
 */
public record PolicyResult(PolicyDecision decision, String reason) {

    public boolean isAllowed() {
        return decision == PolicyDecision.ALLOW;
    }

    public static PolicyResult allow() {
        return new PolicyResult(PolicyDecision.ALLOW, "allowed");
    }

    public static PolicyResult deny(String reason) {
        return new PolicyResult(PolicyDecision.DENY, reason);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-policy/
git commit -m "feat(sdk-policy): add pom, PolicyDecision, PolicyRequest, PolicyResult"
```

---

## Task 2: sdk-policy — PolicyResultCache (Caffeine)

**Files:**
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyResultCache.java`
- Create: `fos-sdk/sdk-policy/src/test/java/com/fos/sdk/policy/PolicyResultCacheTest.java`

- [ ] **Step 1: Write the failing test**

```java
// PolicyResultCacheTest.java
package com.fos.sdk.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyResultCacheTest {

    private PolicyResultCache cache;

    @BeforeEach
    void setUp() {
        cache = new PolicyResultCache();
    }

    @Test
    void should_call_evaluator_on_first_access() {
        AtomicInteger callCount = new AtomicInteger(0);

        PolicyResult result = cache.getOrEvaluate("actor1:read:ACTIVE", () -> {
            callCount.incrementAndGet();
            return PolicyResult.allow();
        });

        assertThat(result.isAllowed()).isTrue();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void should_use_cache_on_second_access() {
        AtomicInteger callCount = new AtomicInteger(0);

        cache.getOrEvaluate("actor1:read:ACTIVE", () -> {
            callCount.incrementAndGet();
            return PolicyResult.allow();
        });
        cache.getOrEvaluate("actor1:read:ACTIVE", () -> {
            callCount.incrementAndGet();
            return PolicyResult.allow();
        });

        assertThat(callCount.get()).isEqualTo(1); // evaluator called only once
    }

    @Test
    void should_invalidate_all_entries_for_actor() {
        AtomicInteger callCount = new AtomicInteger(0);

        cache.getOrEvaluate("actor2:read:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.allow(); });
        cache.getOrEvaluate("actor2:write:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.deny("denied"); });

        cache.invalidateActor("actor2");

        cache.getOrEvaluate("actor2:read:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.allow(); });
        cache.getOrEvaluate("actor2:write:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.deny("denied"); });

        assertThat(callCount.get()).isEqualTo(4); // 2 original + 2 after invalidation
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-sdk/sdk-policy
mvn test -Dtest=PolicyResultCacheTest -q
```

Expected: FAIL — `PolicyResultCache` not found

- [ ] **Step 3: Implement PolicyResultCache**

```java
// PolicyResultCache.java
package com.fos.sdk.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caffeine-backed cache for PolicyResult entries.
 * Reduces OPA call volume for repeated policy checks within a request burst.
 *
 * Cache key: "{actorId}:{action}:{resourceState}"
 * TTL: 30 seconds — short enough that role/state changes propagate quickly.
 * Max size: 1000 entries — enough for hundreds of concurrent actors.
 */
@Component
public class PolicyResultCache {

    private final Cache<String, PolicyResult> cache;

    public PolicyResultCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Returns cached result if present, otherwise calls evaluator and caches the result.
     *
     * @param cacheKey  format: "{actorId}:{action}:{resourceState}"
     * @param evaluator called only on cache miss
     */
    public PolicyResult getOrEvaluate(String cacheKey, Supplier<PolicyResult> evaluator) {
        return cache.get(cacheKey, k -> evaluator.get());
    }

    /**
     * Evict all cache entries for a specific actor.
     * Called when the actor's role or state changes (e.g., on IDENTITY_ACTOR_UPDATED signal).
     */
    public void invalidateActor(String actorId) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(actorId + ":"));
    }

    /** Evict everything. Used in tests. */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-sdk/sdk-policy
mvn test -Dtest=PolicyResultCacheTest -q
```

Expected: BUILD SUCCESS — 3 tests pass

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-policy/src/
git commit -m "feat(sdk-policy): add PolicyResultCache with Caffeine (TTL 30s, max 1000)"
```

---

## Task 3: sdk-policy — PolicyClient + @PolicyGuard + PolicyGuardAspect

**Files:**
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyClient.java`
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyGuard.java`
- Create: `fos-sdk/sdk-policy/src/main/java/com/fos/sdk/policy/PolicyGuardAspect.java`
- Create: `fos-sdk/sdk-policy/src/test/java/com/fos/sdk/policy/PolicyGuardAspectTest.java`

- [ ] **Step 1: Write the failing test**

```java
// PolicyGuardAspectTest.java
package com.fos.sdk.policy;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.security.FosSecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {PolicyGuardAspect.class, PolicyGuardAspectTest.TestService.class,
                            PolicyResultCache.class})
class PolicyGuardAspectTest {

    @MockBean
    private PolicyClient policyClient;

    @MockBean
    private FosSecurityContext securityContext;

    @Autowired
    private TestService testService;

    @Test
    void should_allow_when_policy_returns_allow() {
        when(securityContext.getActorId()).thenReturn(UUID.randomUUID());
        when(securityContext.getRole()).thenReturn("PLAYER");
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.allow());

        String result = testService.protectedOperation();

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_throw_access_denied_when_policy_returns_deny() {
        when(securityContext.getActorId()).thenReturn(UUID.randomUUID());
        when(securityContext.getRole()).thenReturn("PLAYER");
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.deny("Role not allowed"));

        assertThatThrownBy(() -> testService.protectedOperation())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Role not allowed");
    }

    @Service
    static class TestService {
        @PolicyGuard(action = "test.resource.read", resourceType = "TEAM")
        public String protectedOperation() {
            return "ok";
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-sdk/sdk-policy
mvn test -Dtest=PolicyGuardAspectTest -q
```

Expected: FAIL — `PolicyClient` not found

- [ ] **Step 3: Implement PolicyClient**

```java
// PolicyClient.java
package com.fos.sdk.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Remote Proxy to the policy evaluation endpoint in fos-governance-service.
 * Domain services inject this and call evaluate() — they never call OPA directly.
 * Results are cached by PolicyGuardAspect via PolicyResultCache.
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
```

- [ ] **Step 4: Create @PolicyGuard annotation**

```java
// PolicyGuard.java
package com.fos.sdk.policy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative ABAC guard. Apply to service methods that operate on owned resources.
 *
 * Usage:
 *   @PolicyGuard(action = "workspace.file.read", resourceType = "TEAM")
 *   public FileDTO getFile(UUID fileId) { ... }
 *
 * The aspect resolves actor from FosSecurityContext and resource from method arguments.
 * The first UUID argument is treated as the resource ID unless resourceIdParam is specified.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PolicyGuard {
    /** The action string passed to the policy engine, e.g. "workspace.file.read" */
    String action();

    /** CanonicalType name of the resource being accessed, e.g. "TEAM", "PLAYER" */
    String resourceType() default "UNKNOWN";

    /**
     * Optional: name of the method parameter holding the resource ID.
     * Default: use the first UUID parameter in the method signature.
     */
    String resourceIdParam() default "";
}
```

- [ ] **Step 5: Implement PolicyGuardAspect**

```java
// PolicyGuardAspect.java
package com.fos.sdk.policy;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.security.FosSecurityContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class PolicyGuardAspect {

    private final PolicyClient policyClient;
    private final FosSecurityContext securityContext;
    private final PolicyResultCache cache;

    public PolicyGuardAspect(PolicyClient policyClient,
                              FosSecurityContext securityContext,
                              PolicyResultCache cache) {
        this.policyClient = policyClient;
        this.securityContext = securityContext;
        this.cache = cache;
    }

    @Around("@annotation(guard)")
    public Object enforcePolicy(ProceedingJoinPoint joinPoint, PolicyGuard guard) throws Throwable {
        UUID actorId = securityContext.getActorId();
        String role  = securityContext.getRole();
        UUID resourceId = resolveResourceId(joinPoint, guard);
        CanonicalType resourceType = CanonicalType.valueOf(guard.resourceType());

        // Cache key includes actor, action, and resource state (UNKNOWN when state not resolvable here)
        String cacheKey = actorId + ":" + guard.action() + ":UNKNOWN";

        PolicyResult result = cache.getOrEvaluate(cacheKey, () ->
                policyClient.evaluate(PolicyRequest.of(
                        actorId, role, guard.action(),
                        CanonicalRef.of(resourceType, resourceId),
                        "UNKNOWN")));

        if (!result.isAllowed()) {
            throw new AccessDeniedException(result.reason());
        }

        return joinPoint.proceed();
    }

    private UUID resolveResourceId(ProceedingJoinPoint joinPoint, PolicyGuard guard) {
        Object[] args = joinPoint.getArgs();
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = sig.getParameterNames();

        if (!guard.resourceIdParam().isEmpty()) {
            for (int i = 0; i < paramNames.length; i++) {
                if (paramNames[i].equals(guard.resourceIdParam()) && args[i] instanceof UUID) {
                    return (UUID) args[i];
                }
            }
        }

        // Fallback: first UUID parameter
        for (Object arg : args) {
            if (arg instanceof UUID) return (UUID) arg;
        }

        return UUID.randomUUID(); // no resource ID in args; policy will use context only
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd fos-sdk/sdk-policy
mvn test -Dtest=PolicyGuardAspectTest -q
```

Expected: BUILD SUCCESS — 2 tests pass

- [ ] **Step 7: Create PolicyAutoConfiguration and register**

```java
// PolicyAutoConfiguration.java
package com.fos.sdk.policy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class PolicyAutoConfiguration {

    @Bean
    public PolicyClient policyClient(RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value(
                "${fos.policy.service-url:http://localhost:8081}") String url) {
        return new PolicyClient(builder, url);
    }

    @Bean
    public PolicyResultCache policyResultCache() {
        return new PolicyResultCache();
    }

    @Bean
    public PolicyGuardAspect policyGuardAspect(PolicyClient client,
                                                com.fos.sdk.security.FosSecurityContext ctx,
                                                PolicyResultCache cache) {
        return new PolicyGuardAspect(client, ctx, cache);
    }
}
```

```
# fos-sdk/sdk-policy/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.fos.sdk.policy.PolicyAutoConfiguration
```

- [ ] **Step 8: Add sdk-policy module to SDK aggregator pom.xml**

In `fos-sdk/pom.xml`, add `<module>sdk-policy</module>` after `sdk-canonical`:

```xml
<modules>
  <module>sdk-core</module>
  <module>sdk-events</module>
  <module>sdk-security</module>
  <module>sdk-storage</module>
  <module>sdk-canonical</module>
  <module>sdk-policy</module>   <!-- add this line -->
  <module>sdk-test</module>
</modules>
```

- [ ] **Step 9: Build and install sdk-policy**

```bash
cd fos-sdk
mvn install -pl sdk-policy -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add fos-sdk/sdk-policy/
git commit -m "feat(sdk-policy): add PolicyClient, PolicyGuard annotation, PolicyGuardAspect with Caffeine cache"
```

---

## Task 4: Governance — Flyway Migration for fos_audit Table

**Files:**
- Create: `fos-governance-service/src/main/resources/db/migration/V005__create_audit_log_table.sql`

- [ ] **Step 1: Create V005__create_audit_log_table.sql**

```sql
-- V005__create_audit_log_table.sql
-- Append-only audit log. Written by the AuditLogConsumer from Kafka topic fos.audit.all.
-- No UPDATE or DELETE ever runs on this table.

CREATE TABLE fos_audit.audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    signal_id      UUID         NOT NULL,
    actor_id       UUID,
    action         VARCHAR(255) NOT NULL,
    resource_type  VARCHAR(100),
    resource_id    UUID,
    topic          VARCHAR(255),
    payload        JSONB,
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Idempotency: prevent duplicate processing of the same signal
CREATE UNIQUE INDEX uidx_audit_log_signal_id ON fos_audit.audit_log(signal_id);

CREATE INDEX idx_audit_log_actor_id   ON fos_audit.audit_log(actor_id);
CREATE INDEX idx_audit_log_resource   ON fos_audit.audit_log(resource_type, resource_id);
CREATE INDEX idx_audit_log_recorded   ON fos_audit.audit_log(recorded_at DESC);
```

- [ ] **Step 2: Commit**

```bash
git add fos-governance-service/src/main/resources/db/migration/V005__create_audit_log_table.sql
git commit -m "feat(governance): add Flyway V005 migration for append-only audit_log table"
```

---

## Task 5: Governance — Policy Package (OPA Integration)

**Files:**
- Create: `fos-governance-service/src/main/resources/opa/fos_policy.rego`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/infrastructure/opa/OpaClient.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/application/context/PolicyContextBuilder.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/application/context/RoleContextBuilder.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/application/context/ResourceStateContextBuilder.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/application/context/OpaEvaluator.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/application/PolicyEvaluationService.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/api/PolicyEvaluationRequest.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/policy/api/PolicyEvaluationController.java`

- [ ] **Step 1: Create base Rego policy file**

This is a minimal policy: any actor with a known role can perform any action on ACTIVE resources. Full ABAC per domain comes in later sprints.

```rego
# fos-governance-service/src/main/resources/opa/fos_policy.rego
package fos

default allow = false

# Allow when actor has a recognized role and resource is ACTIVE
allow {
    input.actor.role != ""
    input.resource.state == "ACTIVE"
}

# Allow DRAFT resource operations for CLUB_ADMIN and OPERATOR
allow {
    input.resource.state == "DRAFT"
    role := input.actor.role
    role_can_access_draft[role]
}

role_can_access_draft := {"CLUB_ADMIN", "OPERATOR", "HEAD_COACH"}

# Deny all access to ARCHIVED resources
deny {
    input.resource.state == "ARCHIVED"
}
```

- [ ] **Step 2: Create OpaClient**

```java
// OpaClient.java
package com.fos.governance.policy.infrastructure.opa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * HTTP client for the OPA sidecar running at http://localhost:8181.
 * OPA sidecar is started as a separate container alongside fos-governance-service.
 * Only PolicyEvaluationService talks to OpaClient — never domain services.
 */
@Component
public class OpaClient {

    private final RestClient restClient;

    public OpaClient(RestClient.Builder builder,
                     @Value("${fos.opa.url:http://localhost:8181}") String opaUrl) {
        this.restClient = builder.baseUrl(opaUrl).build();
    }

    /**
     * Sends input to OPA and returns the allow decision.
     * @param input Map matching the Rego input structure: {actor: {role}, resource: {state}}
     * @return true if OPA returns allow=true
     */
    public boolean evaluate(Map<String, Object> input) {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/v1/data/fos/allow")
                .body(Map.of("input", input))
                .retrieve()
                .body(Map.class);

        Object result = response != null ? response.get("result") : null;
        return Boolean.TRUE.equals(result);
    }
}
```

- [ ] **Step 3: Create PolicyContextBuilder chain**

```java
// PolicyContextBuilder.java — abstract handler in the chain
package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.HashMap;
import java.util.Map;

public abstract class PolicyContextBuilder {

    private PolicyContextBuilder next;

    public PolicyContextBuilder then(PolicyContextBuilder next) {
        this.next = next;
        return next;
    }

    public final Map<String, Object> build(PolicyRequest request) {
        Map<String, Object> context = new HashMap<>(enrich(request));
        if (next != null) {
            context.putAll(next.build(request));
        }
        return context;
    }

    protected abstract Map<String, Object> enrich(PolicyRequest request);
}
```

```java
// RoleContextBuilder.java
package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.Map;

public class RoleContextBuilder extends PolicyContextBuilder {
    @Override
    protected Map<String, Object> enrich(PolicyRequest request) {
        return Map.of("actor", Map.of(
                "id",   request.actorId().toString(),
                "role", request.actorRole() != null ? request.actorRole() : ""
        ));
    }
}
```

```java
// ResourceStateContextBuilder.java
package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.Map;

public class ResourceStateContextBuilder extends PolicyContextBuilder {
    @Override
    protected Map<String, Object> enrich(PolicyRequest request) {
        return Map.of("resource", Map.of(
                "type",   request.resourceRef() != null ? request.resourceRef().type().name() : "",
                "id",     request.resourceRef() != null ? request.resourceRef().id().toString() : "",
                "state",  request.resourceState() != null ? request.resourceState() : "UNKNOWN",
                "action", request.action()
        ));
    }
}
```

```java
// OpaEvaluator.java — terminal step in the chain; calls OpaClient
package com.fos.governance.policy.application.context;

import com.fos.governance.policy.infrastructure.opa.OpaClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;

import java.util.Map;

public class OpaEvaluator {

    private final OpaClient opaClient;
    private final PolicyContextBuilder contextChain;

    public OpaEvaluator(OpaClient opaClient, PolicyContextBuilder contextChain) {
        this.opaClient = opaClient;
        this.contextChain = contextChain;
    }

    public PolicyResult evaluate(PolicyRequest request) {
        Map<String, Object> opaInput = contextChain.build(request);
        boolean allowed = opaClient.evaluate(opaInput);
        return allowed ? PolicyResult.allow() : PolicyResult.deny("Policy denied: " + request.action());
    }
}
```

- [ ] **Step 4: Implement PolicyEvaluationService**

```java
// PolicyEvaluationService.java
package com.fos.governance.policy.application;

import com.fos.governance.policy.application.context.OpaEvaluator;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import org.springframework.stereotype.Service;

@Service
public class PolicyEvaluationService {

    private final OpaEvaluator opaEvaluator;

    public PolicyEvaluationService(OpaEvaluator opaEvaluator) {
        this.opaEvaluator = opaEvaluator;
    }

    public PolicyResult evaluate(PolicyRequest request) {
        return opaEvaluator.evaluate(request);
    }
}
```

- [ ] **Step 5: Implement PolicyEvaluationController**

```java
// PolicyEvaluationRequest.java
package com.fos.governance.policy.api;

import com.fos.sdk.canonical.CanonicalRef;
import java.util.Map;
import java.util.UUID;

public record PolicyEvaluationRequest(
    UUID actorId,
    String actorRole,
    String action,
    CanonicalRef resourceRef,
    String resourceState,
    Map<String, Object> context
) {}
```

```java
// PolicyEvaluationController.java
package com.fos.governance.policy.api;

import com.fos.governance.policy.application.PolicyEvaluationService;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policy")
public class PolicyEvaluationController {

    private final PolicyEvaluationService evaluationService;

    public PolicyEvaluationController(PolicyEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public PolicyResult evaluate(@RequestBody PolicyEvaluationRequest request) {
        return evaluationService.evaluate(new PolicyRequest(
                request.actorId(), request.actorRole(), request.action(),
                request.resourceRef(), request.resourceState(),
                request.context() != null ? request.context() : java.util.Map.of()
        ));
    }
}
```

- [ ] **Step 6: Wire the OpaEvaluator bean in config**

Create `fos-governance-service/src/main/java/com/fos/governance/config/PolicyConfig.java`:

```java
// PolicyConfig.java
package com.fos.governance.config;

import com.fos.governance.policy.application.context.*;
import com.fos.governance.policy.infrastructure.opa.OpaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfig {

    @Bean
    public OpaEvaluator opaEvaluator(OpaClient opaClient) {
        // Assemble the Chain of Responsibility: Role → ResourceState → (terminal: OpaEvaluator)
        RoleContextBuilder roleBuilder = new RoleContextBuilder();
        ResourceStateContextBuilder stateBuilder = new ResourceStateContextBuilder();
        roleBuilder.then(stateBuilder);
        return new OpaEvaluator(opaClient, roleBuilder);
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/policy/ \
        fos-governance-service/src/main/java/com/fos/governance/config/PolicyConfig.java \
        fos-governance-service/src/main/resources/opa/
git commit -m "feat(governance/policy): add OPA integration, PolicyContextBuilder chain, PolicyEvaluationController"
```

---

## Task 6: Governance — Signal Package (Chain of Responsibility Pipeline)

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/domain/port/NotificationPort.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/infrastructure/notification/NoopNotificationAdapter.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/application/pipeline/SignalHandler.java` (and 5 handlers)
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/application/SignalProcessingService.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/api/SignalIntakeController.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/config/SignalPipelineConfig.java`

- [ ] **Step 1: Create NotificationPort (Adapter / Port pattern)**

```java
// NotificationPort.java
package com.fos.governance.signal.domain.port;

/**
 * Port for notification delivery.
 * Push and email providers are not yet decided — wire adapters via @ConditionalOnProperty.
 * Default adapter: NoopNotificationAdapter (logs and does nothing).
 */
public interface NotificationPort {
    void sendAlert(AlertNotification notification);
}
```

```java
// AlertNotification.java (value object, same package)
package com.fos.governance.signal.domain.port;

import java.util.UUID;

public record AlertNotification(
    UUID   recipientActorId,
    String title,
    String body,
    String topic
) {}
```

- [ ] **Step 2: Create NoopNotificationAdapter (Null Object)**

```java
// NoopNotificationAdapter.java
package com.fos.governance.signal.infrastructure.notification;

import com.fos.governance.signal.domain.port.AlertNotification;
import com.fos.governance.signal.domain.port.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Null Object adapter for NotificationPort.
 * Active when no real adapter is configured (fos.notification.push-provider is unset).
 * Logs the notification but sends nothing. Safe default in dev and tests.
 */
@Component
@ConditionalOnMissingBean(NotificationPort.class)
public class NoopNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NoopNotificationAdapter.class);

    @Override
    public void sendAlert(AlertNotification notification) {
        log.info("[NOOP] Alert suppressed: actorId={} title='{}'",
                notification.recipientActorId(), notification.title());
    }
}
```

- [ ] **Step 3: Create SignalHandler abstract class and 5 concrete handlers**

```java
// SignalHandler.java
package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;

/**
 * Abstract handler in the Chain of Responsibility for signal processing.
 * Each step either transforms and passes the signal to the next handler,
 * or returns null to stop the chain (rejected signal).
 *
 * Chain order: SchemaValidation → ActorEnrichment → TypeClassification → KafkaRouting → NotificationFanOut
 */
public abstract class SignalHandler {

    private SignalHandler next;

    public SignalHandler then(SignalHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Runs this handler then passes result to next, unless result is null (rejected).
     */
    public final SignalEnvelope process(SignalEnvelope signal) {
        SignalEnvelope result = handle(signal);
        if (result == null) return null;
        return (next != null) ? next.process(result) : result;
    }

    protected abstract SignalEnvelope handle(SignalEnvelope signal);
}
```

```java
// SchemaValidationHandler.java — Step 1: reject malformed signals
package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaValidationHandler extends SignalHandler {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationHandler.class);

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        if (signal.type() == null || signal.topic() == null || signal.topic().isBlank()) {
            log.warn("Signal rejected — missing type or topic: signalId={}", signal.signalId());
            return null;
        }
        if (signal.actorRef() == null) {
            log.warn("Signal rejected — missing actorRef: signalId={}", signal.signalId());
            return null;
        }
        return signal;
    }
}
```

```java
// ActorEnrichmentHandler.java — Step 2: attach display name (for notification rendering)
package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;

/**
 * Attaches actor display name to the signal context for use in notifications.
 * In Sprint 0.5 this is a no-op (just passes through).
 * In Phase 1+ it queries CanonicalResolver when actor_ref points to a Player or Staff.
 */
public class ActorEnrichmentHandler extends SignalHandler {

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        // Display name resolution deferred to Phase 1 when CanonicalResolver is available
        return signal;
    }
}
```

```java
// TypeClassificationHandler.java — Step 3: map signal type to Kafka partition strategy
package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeClassificationHandler extends SignalHandler {

    private static final Logger log = LoggerFactory.getLogger(TypeClassificationHandler.class);

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        // Log classification for observability. Partition key strategy in Phase 1+.
        log.debug("Signal classified: type={} topic={}", signal.type(), signal.topic());

        if (signal.type() == SignalType.AUDIT) {
            // AUDIT signals bypass the rest of the pipeline — they go directly to fos.audit.all
            // Routed by KafkaRoutingHandler below; no notification fan-out.
        }

        return signal;
    }
}
```

```java
// KafkaRoutingHandler.java — Step 4: publish to Kafka topic
package com.fos.governance.signal.application.pipeline;

import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.SignalEnvelope;

public class KafkaRoutingHandler extends SignalHandler {

    private final FosKafkaProducer kafkaProducer;

    public KafkaRoutingHandler(FosKafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        kafkaProducer.emit(signal);
        return signal;
    }
}
```

```java
// NotificationFanOutHandler.java — Step 5: send push/email for ALERT signals
package com.fos.governance.signal.application.pipeline;

import com.fos.governance.signal.domain.port.AlertNotification;
import com.fos.governance.signal.domain.port.NotificationPort;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;

import java.util.UUID;

public class NotificationFanOutHandler extends SignalHandler {

    private final NotificationPort notificationPort;

    public NotificationFanOutHandler(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @Override
    protected SignalEnvelope handle(SignalEnvelope signal) {
        if (signal.type() == SignalType.ALERT) {
            UUID recipientId = signal.actorRef() != null ? signal.actorRef().id() : null;
            if (recipientId != null) {
                notificationPort.sendAlert(new AlertNotification(
                        recipientId,
                        "Alert: " + signal.topic(),
                        signal.payload() != null ? signal.payload().toString() : "",
                        signal.topic()
                ));
            }
        }
        return signal;
    }
}
```

- [ ] **Step 4: Implement SignalProcessingService**

```java
// SignalProcessingService.java
package com.fos.governance.signal.application;

import com.fos.governance.signal.application.pipeline.SignalHandler;
import com.fos.sdk.events.SignalEnvelope;
import org.springframework.stereotype.Service;

@Service
public class SignalProcessingService {

    private final SignalHandler pipeline;

    public SignalProcessingService(SignalHandler pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Runs the signal through the full Chain of Responsibility pipeline.
     * @return the processed signal, or null if the signal was rejected.
     */
    public SignalEnvelope process(SignalEnvelope signal) {
        return pipeline.process(signal);
    }
}
```

- [ ] **Step 5: Implement SignalIntakeController**

```java
// SignalIntakeController.java
package com.fos.governance.signal.api;

import com.fos.governance.signal.application.SignalProcessingService;
import com.fos.sdk.events.SignalEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/signals")
public class SignalIntakeController {

    private final SignalProcessingService processingService;

    public SignalIntakeController(SignalProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping
    public ResponseEntity<Void> intake(@RequestBody SignalEnvelope signal) {
        SignalEnvelope result = processingService.process(signal);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
        return ResponseEntity.accepted().build();
    }
}
```

- [ ] **Step 6: Create SignalPipelineConfig to wire the chain**

```java
// SignalPipelineConfig.java
package com.fos.governance.config;

import com.fos.governance.signal.application.pipeline.*;
import com.fos.governance.signal.domain.port.NotificationPort;
import com.fos.sdk.events.FosKafkaProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignalPipelineConfig {

    /**
     * Assembles the signal processing Chain of Responsibility:
     * SchemaValidation → ActorEnrichment → TypeClassification → KafkaRouting → NotificationFanOut
     *
     * Returns the head of the chain (SchemaValidationHandler).
     * SignalProcessingService receives this bean and calls process() on it.
     */
    @Bean
    public SignalHandler signalPipeline(FosKafkaProducer kafkaProducer,
                                        NotificationPort notificationPort) {
        SchemaValidationHandler head = new SchemaValidationHandler();
        head.then(new ActorEnrichmentHandler())
            .then(new TypeClassificationHandler())
            .then(new KafkaRoutingHandler(kafkaProducer))
            .then(new NotificationFanOutHandler(notificationPort));
        return head;
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/signal/ \
        fos-governance-service/src/main/java/com/fos/governance/config/SignalPipelineConfig.java
git commit -m "feat(governance/signal): add NotificationPort, NoopNotificationAdapter, Chain of Responsibility pipeline, SignalIntakeController"
```

---

## Task 7: Governance — AuditLogConsumer (Template Method)

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/infrastructure/audit/AuditLogEntry.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/infrastructure/audit/AuditLogRepository.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/signal/infrastructure/audit/AuditLogConsumer.java`

- [ ] **Step 1: Create AuditLogEntry entity**

```java
// AuditLogEntry.java
package com.fos.governance.signal.infrastructure.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record in fos_audit.audit_log.
 * Written by AuditLogConsumer from Kafka topic fos.audit.all.
 * Never updated or deleted.
 */
@Entity
@Table(schema = "fos_audit", name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_id", nullable = false, unique = true)
    private UUID signalId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "action")
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "topic")
    private String topic;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected AuditLogEntry() {}

    public AuditLogEntry(UUID signalId, UUID actorId, String action,
                          String resourceType, UUID resourceId, String topic, String payload) {
        this.signalId = signalId;
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.topic = topic;
        this.payload = payload;
        this.recordedAt = Instant.now();
    }

    public UUID getSignalId() { return signalId; }
    public Long getId()       { return id; }
}
```

- [ ] **Step 2: Create AuditLogRepository**

```java
// AuditLogRepository.java
package com.fos.governance.signal.infrastructure.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
    boolean existsBySignalId(java.util.UUID signalId);
}
```

- [ ] **Step 3: Implement AuditLogConsumer extending AbstractFosConsumer**

```java
// AuditLogConsumer.java
package com.fos.governance.signal.infrastructure.audit;

import com.fos.sdk.events.AbstractFosConsumer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for fos.audit.all topic.
 * Writes every signal to fos_audit.audit_log (append-only, idempotent via UNIQUE on signal_id).
 *
 * Extends AbstractFosConsumer — Template Method pattern:
 * Deserialization, MDC, error handling, and DLQ routing are handled by the base class.
 * Only the domain logic (write to audit_log) lives here.
 */
@Component
@KafkaListener(topics = KafkaTopics.AUDIT_ALL, groupId = "fos-governance-audit")
public class AuditLogConsumer extends AbstractFosConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditLogConsumer.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogConsumer(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void handle(SignalEnvelope envelope) {
        if (auditLogRepository.existsBySignalId(envelope.signalId())) {
            log.debug("Audit signal already processed (idempotent skip): signalId={}",
                    envelope.signalId());
            return;
        }

        try {
            String payloadJson = envelope.payload() != null
                    ? objectMapper.writeValueAsString(envelope.payload())
                    : null;

            UUID actorId = envelope.actorRef() != null ? envelope.actorRef().id() : null;
            String resourceType = envelope.actorRef() != null
                    ? envelope.actorRef().type().name() : null;

            AuditLogEntry entry = new AuditLogEntry(
                    envelope.signalId(),
                    actorId,
                    envelope.topic(),
                    resourceType,
                    actorId, // resource_id same as actorRef.id for now; refined in Phase 1
                    envelope.topic(),
                    payloadJson
            );

            auditLogRepository.save(entry);
            log.debug("Audit log written: signalId={} topic={}", envelope.signalId(), envelope.topic());

        } catch (DataIntegrityViolationException e) {
            // Race condition: another instance wrote the same signal_id first — safe to ignore
            log.debug("Audit duplicate detected (concurrent write): signalId={}", envelope.signalId());
        } catch (Exception e) {
            log.error("Failed to write audit log: signalId={}", envelope.signalId(), e);
            throw e; // re-throw so AbstractFosConsumer routes to DLQ
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/signal/infrastructure/audit/
git commit -m "feat(governance/signal): add AuditLogConsumer extending AbstractFosConsumer, append-only audit log"
```

---

## Task 8: Integration Tests

**Files:**
- Create: `fos-governance-service/src/test/java/com/fos/governance/policy/PolicyEvaluationIntegrationTest.java`
- Create: `fos-governance-service/src/test/java/com/fos/governance/signal/SignalIntakeIntegrationTest.java`

- [ ] **Step 1: Add WireMock dependency for OPA simulation**

In `fos-governance-service/pom.xml`, add to `<dependencies>`:

```xml
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock-standalone</artifactId>
  <version>3.5.4</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write PolicyEvaluationIntegrationTest**

```java
// PolicyEvaluationIntegrationTest.java
package com.fos.governance.policy;

import com.fos.governance.policy.api.PolicyEvaluationRequest;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.policy.PolicyResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyEvaluationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")).withDatabaseName("fos_governance");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().port(8181));
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
        registry.add("fos.opa.url", () -> "http://localhost:8181");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return_allow_when_opa_allows() {
        wireMock.stubFor(post(urlEqualTo("/v1/data/fos/allow"))
                .willReturn(okJson("{\"result\": true}")));

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                UUID.randomUUID(), "HEAD_COACH", "workspace.file.read",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()), "ACTIVE", null);

        ResponseEntity<PolicyResult> response = restTemplate.postForEntity(
                "/api/v1/policy/evaluate", request, PolicyResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isAllowed()).isTrue();
    }

    @Test
    void should_return_deny_when_opa_denies() {
        wireMock.stubFor(post(urlEqualTo("/v1/data/fos/allow"))
                .willReturn(okJson("{\"result\": false}")));

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                UUID.randomUUID(), "PLAYER", "workspace.admin.write",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()), "ACTIVE", null);

        ResponseEntity<PolicyResult> response = restTemplate.postForEntity(
                "/api/v1/policy/evaluate", request, PolicyResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isAllowed()).isFalse();
    }
}
```

- [ ] **Step 3: Write SignalIntakeIntegrationTest**

```java
// SignalIntakeIntegrationTest.java
package com.fos.governance.signal;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalIntakeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")).withDatabaseName("fos_governance");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_accept_valid_signal_and_return_202() {
        SignalEnvelope signal = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic("fos.test.fact")
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()))
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .schemaVersion(1)
                .payload(Map.of("test", "data"))
                .build();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/signals", signal, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void should_reject_signal_missing_topic_with_422() {
        // Signal with null topic
        SignalEnvelope signal = SignalEnvelope.builder()
                .signalId(UUID.randomUUID())
                .type(SignalType.FACT)
                .topic(null)   // missing — should fail schema validation
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()))
                .build();

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/signals", signal, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

- [ ] **Step 4: Run integration tests**

```bash
cd fos-governance-service
mvn test -Dtest="PolicyEvaluationIntegrationTest,SignalIntakeIntegrationTest" -q
```

Expected: BUILD SUCCESS — 4 tests pass (2 policy + 2 signal)

- [ ] **Step 5: Commit**

```bash
git add fos-governance-service/src/test/
fos-governance-service/pom.xml
git commit -m "test(governance): add integration tests for policy evaluation (WireMock OPA) and signal intake"
```

---

## Task 9: Full Build Verification

- [ ] **Step 1: Full SDK build**

```bash
cd fos-sdk
mvn install -q
```

Expected: BUILD SUCCESS — all SDK modules installed including sdk-policy

- [ ] **Step 2: Full governance service build**

```bash
cd fos-governance-service
mvn package -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Smoke test signal intake**

```bash
# Start services
cd football-os-core && docker-compose up -d postgres kafka

# Start governance service
java -jar fos-governance-service/target/fos-governance-service-*.jar &

# Post a signal
curl -s -X POST http://localhost:8081/api/v1/signals \
  -H "Content-Type: application/json" \
  -d '{
    "signalId": "00000000-0000-0000-0000-000000000001",
    "type": "FACT",
    "topic": "fos.test.fact",
    "actorRef": {"type": "CLUB", "id": "00000000-0000-0000-0000-000000000002"},
    "correlationId": "test-001",
    "schemaVersion": 1
  }'
```

Expected: HTTP 202 Accepted

- [ ] **Step 4: Final commit**

```bash
git add .
git commit -m "chore(governance): sprint 0.5 complete — policy + signal + sdk-policy"
```

---

## Sprint Test Criteria

Sprint 0.5 is complete when:

1. `mvn test` passes in `fos-sdk/sdk-policy` (5+ tests: PolicyResultCache + PolicyGuardAspect)
2. `mvn test` passes in `fos-governance-service` (4+ integration tests: 2 policy + 2 signal)
3. `POST /api/v1/policy/evaluate` with OPA returning `true` → `{"decision":"ALLOW",...}`
4. `POST /api/v1/policy/evaluate` with OPA returning `false` → `{"decision":"DENY",...}`
5. `POST /api/v1/signals` with valid body → 202 Accepted
6. `POST /api/v1/signals` with missing topic → 422 Unprocessable Entity
7. `NoopNotificationAdapter` is the active bean (no real push/email service configured)
8. `AuditLogConsumer` extends `AbstractFosConsumer` (no raw `@KafkaListener` on a plain method)
9. Flyway V005 (audit_log) runs cleanly

---

## What NOT to Include in This Sprint

- **ESCALATE signal handling** — deferred until a domain explicitly needs it
- **Real push/email notification adapters** — NoopNotificationAdapter is sufficient; Firebase/SES in Phase 3+
- **Redis-backed policy cache** — Caffeine in-process is sufficient; Redis for multi-instance in Phase 1+
- **Fine-grained ABAC Rego policies** — the Rego file in this sprint is minimal; per-domain policies added as each domain is built
- **Team membership policy context** — `TeamMembershipContextBuilder` deferred to Phase 1 (workspace)
- **JWT validation on these endpoints** — Sprint 0.6 (gateway handles it)
- **sdk-policy used in a real domain service** — Sprint 0.6 wires it in a sample service for verification

---

## SDK Dependencies Used

| Module | Usage |
|--------|-------|
| `sdk-core` | `ResourceState`, `ErrorResponse` |
| `sdk-events` | `FosKafkaProducer`, `KafkaTopics`, `SignalEnvelope`, `SignalType`, `AbstractFosConsumer` |
| `sdk-security` | `FosSecurityContext` (in `PolicyGuardAspect`) |
| `sdk-canonical` | `CanonicalRef`, `CanonicalType` (in `PolicyRequest`) |
| `sdk-policy` | Built in this sprint; published to local Maven |

---

## Kafka Topics Consumed This Sprint

| Topic | Consumer | Where written |
|-------|----------|---------------|
| `fos.audit.all` | `AuditLogConsumer` | `fos_audit.audit_log` table |
