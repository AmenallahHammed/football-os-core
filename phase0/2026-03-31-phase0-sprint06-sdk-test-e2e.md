# Phase 0 Sprint 0.6 — sdk-test + End-to-End Smoke Test

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `sdk-test` is built and installed with `FosTestContainersBase`, `MockActorFactory`, `SignalCaptor`, and `MockCanonicalResolver`. All existing integration tests in the monorepo are migrated to extend `FosTestContainersBase`. A final end-to-end smoke test confirms the full Phase 0 flow: actor created → JWT issued → policy checked → file uploaded via `sdk-storage` → audit logged.

**Architecture:** `sdk-test` is a library module in `fos-sdk` — test scope only. It removes Testcontainers boilerplate from every integration test. `MockActorFactory` is an Abstract Factory (see DESIGN-PATTERNS.md §10) that produces internally consistent test actors + JWTs. `SignalCaptor` wraps an embedded Kafka consumer to assert signal emissions without sleep/poll. `MockCanonicalResolver` is a Null Object that returns deterministic test DTOs. The E2E smoke test starts the full stack (gateway + governance) and exercises every layer.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Testcontainers 1.19.8, Nimbus JOSE JWT 9.x, Spring Kafka test support, WireMock 3.5.x

**Required Patterns This Sprint:**
- `[REQUIRED]` **Abstract Factory** — `MockActorFactory` creates families of consistent test objects (actor + JWT + canonical ref)
- `[REQUIRED]` **Null Object** — `MockCanonicalResolver` extends `CanonicalResolver`, returns fixed test DTOs with no HTTP calls

---

## File Map

```
fos-sdk/sdk-test/
├── pom.xml                                                               CREATE
└── src/
    ├── main/java/com/fos/sdk/test/
    │   ├── FosTestContainersBase.java                                    CREATE
    │   ├── MockActorFactory.java                                         CREATE
    │   ├── TestActor.java                                                CREATE
    │   ├── SignalCaptor.java                                             CREATE
    │   └── MockCanonicalResolver.java                                    CREATE
    └── test/java/com/fos/sdk/test/
        ├── MockActorFactoryTest.java                                     CREATE
        └── SignalCaptorTest.java                                         CREATE

fos-sdk/pom.xml                                                           MODIFY (verify sdk-test is last module)

fos-governance-service/src/test/java/com/fos/governance/
├── identity/ActorIntegrationTest.java                                    MODIFY (extend FosTestContainersBase)
├── canonical/CanonicalIntegrationTest.java                              MODIFY (extend FosTestContainersBase)
├── policy/PolicyEvaluationIntegrationTest.java                          MODIFY (extend FosTestContainersBase)
├── signal/SignalIntakeIntegrationTest.java                              MODIFY (extend FosTestContainersBase)
└── SdkClientWiringTest.java                                             MODIFY (extend FosTestContainersBase)

fos-governance-service/src/test/java/com/fos/governance/
└── e2e/Phase0SmokeTest.java                                             CREATE
```

---

## Task 1: sdk-test — pom.xml + TestActor

**Files:**
- Create: `fos-sdk/sdk-test/pom.xml`
- Create: `fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/TestActor.java`

- [ ] **Step 1: Create sdk-test pom.xml**

```xml
<!-- fos-sdk/sdk-test/pom.xml -->
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

  <artifactId>sdk-test</artifactId>
  <name>FOS SDK :: Test</name>
  <description>Test utilities: FosTestContainersBase, MockActorFactory, SignalCaptor, MockCanonicalResolver</description>

  <dependencies>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-events</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-security</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-canonical</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Spring Boot test support -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
    </dependency>

    <!-- Testcontainers -->
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>1.19.8</version>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>postgresql</artifactId>
      <version>1.19.8</version>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mongodb</artifactId>
      <version>1.19.8</version>
    </dependency>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>kafka</artifactId>
      <version>1.19.8</version>
    </dependency>

    <!-- Nimbus JOSE JWT for MockActorFactory -->
    <dependency>
      <groupId>com.nimbusds</groupId>
      <artifactId>nimbus-jose-jwt</artifactId>
      <!-- version managed by Spring Boot BOM -->
    </dependency>

    <!-- Spring Kafka for SignalCaptor -->
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka-test</artifactId>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Create TestActor record**

```java
// TestActor.java
package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalRef;

import java.util.UUID;

/**
 * A test actor: a consistent triple of (actorId, signedJwt, canonicalRef).
 * All three are internally consistent — the actorId matches the JWT sub and the CanonicalRef id.
 * Created by MockActorFactory — never constructed directly in tests.
 */
public record TestActor(
    UUID        actorId,
    String      signedJwt,
    String      role,
    CanonicalRef canonicalRef
) {
    /** Returns the Authorization header value: "Bearer {jwt}" */
    public String authorizationHeader() {
        return "Bearer " + signedJwt;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add fos-sdk/sdk-test/pom.xml \
        fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/TestActor.java
git commit -m "feat(sdk-test): add pom.xml and TestActor record"
```

---

## Task 2: sdk-test — MockActorFactory (Abstract Factory Pattern)

**Files:**
- Create: `fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/MockActorFactory.java`
- Create: `fos-sdk/sdk-test/src/test/java/com/fos/sdk/test/MockActorFactoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
// MockActorFactoryTest.java
package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.FosRoles;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockActorFactoryTest {

    @Test
    void should_create_player_actor_with_consistent_ids() throws Exception {
        TestActor actor = MockActorFactory.player();

        assertThat(actor.role()).isEqualTo(FosRoles.PLAYER);
        assertThat(actor.actorId()).isNotNull();
        assertThat(actor.canonicalRef().type()).isEqualTo(CanonicalType.PLAYER);

        // JWT sub must match actorId
        SignedJWT jwt = SignedJWT.parse(actor.signedJwt());
        String sub = jwt.getJWTClaimsSet().getSubject();
        assertThat(sub).isEqualTo(actor.actorId().toString());
    }

    @Test
    void should_create_head_coach_actor() throws Exception {
        TestActor actor = MockActorFactory.headCoach();

        assertThat(actor.role()).isEqualTo(FosRoles.HEAD_COACH);
        SignedJWT jwt = SignedJWT.parse(actor.signedJwt());
        assertThat(jwt.getJWTClaimsSet().getClaim("role")).isEqualTo(FosRoles.HEAD_COACH);
    }

    @Test
    void should_create_club_admin_actor() throws Exception {
        TestActor actor = MockActorFactory.clubAdmin();

        assertThat(actor.role()).isEqualTo(FosRoles.CLUB_ADMIN);
    }

    @Test
    void should_create_two_distinct_actors_for_same_role() {
        TestActor a1 = MockActorFactory.player();
        TestActor a2 = MockActorFactory.player();

        assertThat(a1.actorId()).isNotEqualTo(a2.actorId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-sdk/sdk-test
mvn test -Dtest=MockActorFactoryTest -q
```

Expected: FAIL — `MockActorFactory` not found

- [ ] **Step 3: Implement MockActorFactory**

```java
// MockActorFactory.java
package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.FosRoles;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.Date;
import java.util.UUID;

/**
 * Abstract Factory for test actors.
 * Creates families of consistent test objects: actor ID + signed JWT + canonical ref.
 * The JWT is HMAC-signed with a fixed test secret — it is NOT valid against Keycloak.
 *
 * Usage:
 *   TestActor player = MockActorFactory.player();
 *   restTemplate.get("/api/v1/resource")
 *               .header("Authorization", player.authorizationHeader())
 *               ...
 */
public final class MockActorFactory {

    /** Fixed test secret for HMAC signing — 32 bytes minimum for HS256. */
    private static final String TEST_SECRET = "fos-test-secret-key-32-bytes-min!!";

    private MockActorFactory() {}

    public static TestActor player() {
        return build(FosRoles.PLAYER, CanonicalType.PLAYER);
    }

    public static TestActor headCoach() {
        return build(FosRoles.HEAD_COACH, CanonicalType.CLUB);
    }

    public static TestActor assistantCoach() {
        return build(FosRoles.ASSISTANT_COACH, CanonicalType.CLUB);
    }

    public static TestActor medicalStaff() {
        return build(FosRoles.MEDICAL_STAFF, CanonicalType.CLUB);
    }

    public static TestActor analyst() {
        return build(FosRoles.ANALYST, CanonicalType.CLUB);
    }

    public static TestActor clubAdmin() {
        return build(FosRoles.CLUB_ADMIN, CanonicalType.CLUB);
    }

    public static TestActor operator() {
        return build(FosRoles.OPERATOR, CanonicalType.CLUB);
    }

    private static TestActor build(String role, CanonicalType refType) {
        UUID actorId = UUID.randomUUID();
        String jwt = signJwt(actorId, role);
        CanonicalRef ref = CanonicalRef.of(refType, actorId);
        return new TestActor(actorId, jwt, role, ref);
    }

    private static String signJwt(UUID actorId, String role) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(actorId.toString())
                    .issuer("http://localhost/realms/fos")
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                    .claim("role", role)
                    .claim("preferred_username", role.toLowerCase() + "-test")
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims);
            jwt.sign(new MACSigner(TEST_SECRET));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
    }

    /** Returns the HMAC secret used to sign test JWTs — for configuring test Spring Security. */
    public static String testJwtSecret() {
        return TEST_SECRET;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-sdk/sdk-test
mvn test -Dtest=MockActorFactoryTest -q
```

Expected: BUILD SUCCESS — 4 tests pass

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-test/src/
git commit -m "feat(sdk-test): add MockActorFactory abstract factory — creates consistent TestActor families"
```

---

## Task 3: sdk-test — FosTestContainersBase

**Files:**
- Create: `fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/FosTestContainersBase.java`

- [ ] **Step 1: Implement FosTestContainersBase**

```java
// FosTestContainersBase.java
package com.fos.sdk.test;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for all integration tests in the Football OS monorepo.
 * Starts PostgreSQL, MongoDB, and Kafka containers once per test class lifecycle.
 *
 * Services that use only PostgreSQL: extend this and use only postgres properties.
 * Services that use only MongoDB: extend this and use only mongo properties.
 * Both databases are started regardless — Testcontainers startup overhead is low.
 *
 * Usage:
 *   @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 *   class MyIntegrationTest extends FosTestContainersBase { ... }
 */
@Testcontainers
public abstract class FosTestContainersBase {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("fos_test")
                    .withReuse(true);  // reuse container across test classes (faster CI)

    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
                    .withReuse(true);

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"))
                    .withReuse(true);

    static {
        POSTGRES.start();
        MONGO.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // MongoDB
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/FosTestContainersBase.java
git commit -m "feat(sdk-test): add FosTestContainersBase with reusable PostgreSQL + MongoDB + Kafka containers"
```

---

## Task 4: sdk-test — SignalCaptor

**Files:**
- Create: `fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/SignalCaptor.java`
- Create: `fos-sdk/sdk-test/src/test/java/com/fos/sdk/test/SignalCaptorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// SignalCaptorTest.java
package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SignalCaptorTest extends FosTestContainersBase {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private SignalCaptor signalCaptor;

    @Test
    void should_capture_emitted_signal() throws Exception {
        String testTopic = "fos.test.signal.captor";
        UUID signalId = UUID.randomUUID();

        SignalEnvelope envelope = SignalEnvelope.builder()
                .signalId(signalId)
                .type(SignalType.FACT)
                .topic(testTopic)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()))
                .correlationId("test-corr-001")
                .timestamp(Instant.now())
                .schemaVersion(1)
                .payload(Map.of("key", "value"))
                .build();

        // Emit directly to Kafka
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        kafkaTemplate.send(testTopic, mapper.writeValueAsString(envelope));

        // Capture without sleep
        SignalEnvelope captured = signalCaptor.waitForSignal(testTopic, 5_000);

        assertThat(captured).isNotNull();
        assertThat(captured.signalId()).isEqualTo(signalId);
        assertThat(captured.type()).isEqualTo(SignalType.FACT);
    }

    @Test
    void should_return_null_when_no_signal_emitted() {
        String emptyTopic = "fos.test.empty.topic." + UUID.randomUUID();

        SignalEnvelope captured = signalCaptor.waitForSignal(emptyTopic, 1_000); // 1s timeout

        assertThat(captured).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-sdk/sdk-test
mvn test -Dtest=SignalCaptorTest -q
```

Expected: FAIL — `SignalCaptor` bean not found

- [ ] **Step 3: Implement SignalCaptor**

```java
// SignalCaptor.java
package com.fos.sdk.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fos.sdk.events.SignalEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test utility for asserting Kafka signals without sleep() or polling.
 * Subscribes to topics on-demand and blocks for up to a timeout waiting for a signal.
 *
 * Usage in tests:
 *   SignalEnvelope signal = signalCaptor.waitForSignal("fos.identity.actor.created", 5_000);
 *   assertThat(signal).isNotNull();
 *   assertThat(signal.type()).isEqualTo(SignalType.FACT);
 */
@Component
public class SignalCaptor {

    private final ConsumerFactory<String, String> consumerFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, BlockingQueue<SignalEnvelope>> queues = new ConcurrentHashMap<>();

    public SignalCaptor(ConsumerFactory<String, String> consumerFactory) {
        this.consumerFactory = consumerFactory;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Waits up to timeoutMillis for a signal on the given topic.
     * @return the captured SignalEnvelope, or null if timeout exceeded
     */
    public SignalEnvelope waitForSignal(String topic, long timeoutMillis) {
        BlockingQueue<SignalEnvelope> queue = queues.computeIfAbsent(topic, t -> {
            LinkedBlockingQueue<SignalEnvelope> q = new LinkedBlockingQueue<>();
            startListener(t, q);
            return q;
        });

        try {
            return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Clears all captured signals. Call in @BeforeEach if tests share topics. */
    public void reset() {
        queues.values().forEach(BlockingQueue::clear);
    }

    private void startListener(String topic, BlockingQueue<SignalEnvelope> queue) {
        ContainerProperties props = new ContainerProperties(topic);
        props.setGroupId("fos-test-captor-" + topic.replace(".", "-"));
        props.setMessageListener((MessageListener<String, String>) record -> {
            try {
                SignalEnvelope envelope = objectMapper.readValue(
                        record.value(), SignalEnvelope.class);
                queue.offer(envelope);
            } catch (Exception e) {
                // Ignore unparseable messages in test context
            }
        });

        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, props);
        container.start();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-sdk/sdk-test
mvn test -Dtest=SignalCaptorTest -q
```

Expected: BUILD SUCCESS — 2 tests pass

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-test/src/
git commit -m "feat(sdk-test): add SignalCaptor — assert Kafka signals without sleep/polling"
```

---

## Task 5: sdk-test — MockCanonicalResolver (Null Object)

**Files:**
- Create: `fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/MockCanonicalResolver.java`

- [ ] **Step 1: Implement MockCanonicalResolver**

```java
// MockCanonicalResolver.java
package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalResolver;
import com.fos.sdk.canonical.CanonicalServiceClient;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.canonical.TeamDTO;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Null Object / test stub for CanonicalResolver.
 * Returns deterministic test DTOs without making any HTTP calls.
 * Use this in tests that do not test canonical integration specifically.
 *
 * Wire as the primary bean in tests by annotating the test class:
 *   @Import(MockCanonicalResolver.class)
 * Or declare it as @TestConfiguration @Bean.
 */
public class MockCanonicalResolver extends CanonicalResolver {

    public MockCanonicalResolver() {
        super(null); // no real client needed
    }

    @Override
    public PlayerDTO getPlayer(UUID id) {
        return new PlayerDTO(
                id,
                "Test Player " + id.toString().substring(0, 8),
                "CF",
                "TS",  // Test nationality
                LocalDate.of(1995, 1, 1),
                null
        );
    }

    @Override
    public TeamDTO getTeam(UUID id) {
        return new TeamDTO(
                id,
                "Test FC " + id.toString().substring(0, 8),
                "TFC",
                "TS",
                null
        );
    }

    @Override
    public void evict(com.fos.sdk.canonical.CanonicalRef ref) {
        // no-op — nothing to evict from an in-memory stub
    }

    @Override
    public void evictAll() {
        // no-op
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add fos-sdk/sdk-test/src/main/java/com/fos/sdk/test/MockCanonicalResolver.java
git commit -m "feat(sdk-test): add MockCanonicalResolver Null Object — returns test DTOs without HTTP"
```

---

## Task 6: Build and Install sdk-test

- [ ] **Step 1: Verify sdk-test is the last module in SDK aggregator**

In `fos-sdk/pom.xml`, confirm module order is:

```xml
<modules>
  <module>sdk-core</module>
  <module>sdk-events</module>
  <module>sdk-security</module>
  <module>sdk-storage</module>
  <module>sdk-canonical</module>
  <module>sdk-policy</module>
  <module>sdk-test</module>   <!-- must be last — depends on all others -->
</modules>
```

- [ ] **Step 2: Build and install**

```bash
cd fos-sdk
mvn install -q
```

Expected: BUILD SUCCESS — sdk-test-1.0.0-SNAPSHOT installed

- [ ] **Step 3: Add sdk-test dependency to fos-governance-service pom.xml**

```xml
<dependency>
  <groupId>com.fos</groupId>
  <artifactId>sdk-test</artifactId>
  <version>${fos.sdk.version}</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Commit**

```bash
git add fos-sdk/pom.xml fos-governance-service/pom.xml
git commit -m "feat(sdk-test): install sdk-test, add as test dependency in fos-governance-service"
```

---

## Task 7: Migrate Existing Integration Tests to FosTestContainersBase

**Files:**
- Modify: `fos-governance-service/src/test/java/com/fos/governance/identity/ActorIntegrationTest.java`
- Modify: `fos-governance-service/src/test/java/com/fos/governance/canonical/CanonicalIntegrationTest.java`
- Modify: `fos-governance-service/src/test/java/com/fos/governance/policy/PolicyEvaluationIntegrationTest.java`
- Modify: `fos-governance-service/src/test/java/com/fos/governance/signal/SignalIntakeIntegrationTest.java`
- Modify: `fos-governance-service/src/test/java/com/fos/governance/SdkClientWiringTest.java`

Each migration follows the same pattern: remove the inline `@Container` declarations, `@DynamicPropertySource`, and `@Testcontainers` annotation, and instead extend `FosTestContainersBase`.

- [ ] **Step 1: Migrate ActorIntegrationTest**

Replace the test class header and remove inline container setup. The test body (all `@Test` methods) stays unchanged.

**Before:**
```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(...)...;

    @Container
    static KafkaContainer kafka = new KafkaContainer(...);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        ...
    }
    // test methods unchanged
}
```

**After:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActorIntegrationTest extends FosTestContainersBase {
    // @Container, @DynamicPropertySource removed — handled by FosTestContainersBase
    // All @Test methods unchanged
}
```

- [ ] **Step 2: Migrate CanonicalIntegrationTest**

Apply the same transformation (remove containers + `@DynamicPropertySource`, extend `FosTestContainersBase`).

- [ ] **Step 3: Migrate PolicyEvaluationIntegrationTest**

This test also uses WireMock for OPA simulation. Keep WireMock setup intact. Remove only the Testcontainers parts.

**After:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyEvaluationIntegrationTest extends FosTestContainersBase {
    // WireMock setup (@BeforeAll, @AfterAll) stays
    // @DynamicPropertySource only adds fos.opa.url — remove postgres/kafka from it
    @DynamicPropertySource
    static void configureOpa(DynamicPropertyRegistry registry) {
        registry.add("fos.opa.url", () -> "http://localhost:8181");
    }
    // @Test methods unchanged
}
```

- [ ] **Step 4: Migrate SignalIntakeIntegrationTest and SdkClientWiringTest**

Apply same transformation to both files.

- [ ] **Step 5: Run all tests after migration**

```bash
cd fos-governance-service
mvn test -q
```

Expected: BUILD SUCCESS — all tests pass using shared container infrastructure

- [ ] **Step 6: Commit**

```bash
git add fos-governance-service/src/test/
git commit -m "refactor(governance): migrate all integration tests to extend FosTestContainersBase"
```

---

## Task 8: End-to-End Phase 0 Smoke Test

**Files:**
- Create: `fos-governance-service/src/test/java/com/fos/governance/e2e/Phase0SmokeTest.java`

This test exercises the full Phase 0 flow in a single test run:
1. Actor created via `POST /api/v1/actors`
2. Player created via `POST /api/v1/players`
3. Policy evaluated via `POST /api/v1/policy/evaluate` (with WireMock OPA → ALLOW)
4. File upload URL generated via `sdk-storage` (NoopStorageAdapter returns a test URL)
5. Audit signal emitted to `fos.audit.all` topic — captured by `SignalCaptor`
6. `SignalCaptor` confirms the signal arrives without sleep

- [ ] **Step 1: Write Phase0SmokeTest**

```java
// Phase0SmokeTest.java
package com.fos.governance.e2e;

import com.fos.governance.canonical.api.PlayerRequest;
import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.api.ActorResponse;
import com.fos.governance.policy.api.PolicyEvaluationRequest;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.policy.PolicyDecision;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import com.fos.sdk.test.FosTestContainersBase;
import com.fos.sdk.test.MockActorFactory;
import com.fos.sdk.test.SignalCaptor;
import com.fos.sdk.test.TestActor;
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

import java.time.LocalDate;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 End-to-End Smoke Test.
 *
 * Exercises the full governance + SDK flow:
 * 1. Create actor
 * 2. Create player in canonical
 * 3. Evaluate policy (OPA via WireMock)
 * 4. Generate storage upload URL (NoopStorageAdapter)
 * 5. Confirm FACT signals appear on Kafka topics
 *
 * This test uses sdk-test utilities throughout:
 * - FosTestContainersBase for infrastructure
 * - MockActorFactory for test actors
 * - SignalCaptor for Kafka assertions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Phase0SmokeTest extends FosTestContainersBase {

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
        registry.add("fos.opa.url", () -> "http://localhost:8181");
        registry.add("fos.storage.provider", () -> "noop");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SignalCaptor signalCaptor;

    @Autowired
    private StoragePort storagePort;

    // ─────────────────────────────────────────────────────────────────────
    // Step 1: Create actor
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void step1_create_actor() {
        TestActor testActor = MockActorFactory.headCoach();

        ActorRequest request = new ActorRequest(
                testActor.actorId() + "@club.com",
                "Marco", "Rossi",
                com.fos.governance.identity.domain.ActorRole.HEAD_COACH,
                UUID.randomUUID());

        ResponseEntity<ActorResponse> response = restTemplate.postForEntity(
                "/api/v1/actors", request, ActorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo(
                com.fos.governance.identity.domain.ActorRole.HEAD_COACH);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 2: Create player in canonical
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void step2_create_canonical_player() {
        PlayerRequest request = new PlayerRequest(
                "Carlos Silva", "ST", "BR", LocalDate.of(1997, 4, 12), null);

        ResponseEntity<PlayerDTO> response = restTemplate.postForEntity(
                "/api/v1/players", request, PlayerDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Carlos Silva");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 3: Evaluate policy (OPA via WireMock → ALLOW)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void step3_policy_check_returns_allow() {
        wireMock.stubFor(post(urlEqualTo("/v1/data/fos/allow"))
                .willReturn(okJson("{\"result\": true}")));

        PolicyEvaluationRequest request = new PolicyEvaluationRequest(
                UUID.randomUUID(), "HEAD_COACH", "workspace.space.read",
                CanonicalRef.of(CanonicalType.TEAM, UUID.randomUUID()), "ACTIVE", null);

        ResponseEntity<PolicyResult> response = restTemplate.postForEntity(
                "/api/v1/policy/evaluate", request, PolicyResult.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 4: Storage upload URL via sdk-storage (NoopStorageAdapter)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void step4_storage_generates_noop_upload_url() {
        PresignedUploadUrl url = storagePort.generateUploadUrl(
                "test-bucket", "test/path/document.pdf", 3600);

        // NoopStorageAdapter returns a safe default — not null
        assertThat(url).isNotNull();
        assertThat(url.objectKey()).isEqualTo("test/path/document.pdf");
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 5: FACT signals appear on Kafka — captured without sleep
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void step5_actor_created_fact_signal_on_kafka() {
        // Trigger actor creation (which emits FACT signal)
        TestActor testActor = MockActorFactory.player();
        ActorRequest request = new ActorRequest(
                "signal-test-" + testActor.actorId() + "@club.com",
                "Signal", "Test",
                com.fos.governance.identity.domain.ActorRole.PLAYER,
                UUID.randomUUID());

        restTemplate.postForEntity("/api/v1/actors", request, ActorResponse.class);

        // Wait for the FACT signal on Kafka — no sleep needed
        SignalEnvelope signal = signalCaptor.waitForSignal(
                KafkaTopics.IDENTITY_ACTOR_CREATED, 10_000);

        assertThat(signal).isNotNull();
        assertThat(signal.type()).isEqualTo(SignalType.FACT);
        assertThat(signal.topic()).isEqualTo(KafkaTopics.IDENTITY_ACTOR_CREATED);
    }

    @Test
    void step5_player_created_fact_signal_on_kafka() {
        PlayerRequest request = new PlayerRequest(
                "Signal Player " + UUID.randomUUID(), "CM", "ES",
                LocalDate.of(1998, 7, 20), null);

        restTemplate.postForEntity("/api/v1/players", request, PlayerDTO.class);

        SignalEnvelope signal = signalCaptor.waitForSignal(
                KafkaTopics.CANONICAL_PLAYER_CREATED, 10_000);

        assertThat(signal).isNotNull();
        assertThat(signal.type()).isEqualTo(SignalType.FACT);
    }
}
```

- [ ] **Step 2: Run the E2E smoke test**

```bash
cd fos-governance-service
mvn test -Dtest=Phase0SmokeTest -q
```

Expected: BUILD SUCCESS — 5 tests pass (one per step)

- [ ] **Step 3: Commit**

```bash
git add fos-governance-service/src/test/java/com/fos/governance/e2e/
git commit -m "test(governance/e2e): add Phase0SmokeTest — full flow: actor + canonical + policy + storage + Kafka signals"
```

---

## Task 9: Full Monorepo Build and Test Verification

- [ ] **Step 1: Full monorepo build**

```bash
cd football-os-core
mvn package -q
```

Expected: BUILD SUCCESS — all 9 modules (fos-sdk × 7, fos-governance-service, fos-gateway)

- [ ] **Step 2: Run all tests**

```bash
cd football-os-core
mvn test -q
```

Expected: BUILD SUCCESS — all integration tests pass

- [ ] **Step 3: Count total test coverage**

```bash
cd football-os-core
mvn test -q 2>&1 | grep "Tests run:" | awk -F',' '{print $1}' | awk '{sum += $NF} END {print "Total tests:", sum}'
```

Expected: 30+ tests across all modules

- [ ] **Step 4: Final tag for Phase 0 completion**

```bash
git tag phase-0-complete -m "Phase 0 complete: SDK + governance + gateway all green"
```

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "chore: Phase 0 sprint 0.6 complete — sdk-test, FosTestContainersBase, MockActorFactory, SignalCaptor, E2E smoke test"
```

---

## Sprint Test Criteria

Sprint 0.6 is complete when:

1. `mvn test` passes in `fos-sdk/sdk-test` (6+ tests: MockActorFactory + SignalCaptor)
2. `mvn test` passes in `fos-governance-service` (all existing tests + Phase0SmokeTest)
3. `Phase0SmokeTest` passes all 5 steps without any `Thread.sleep()` calls
4. All 5 governance integration test classes extend `FosTestContainersBase` (no duplicate container declarations)
5. `MockActorFactory.player()` returns a `TestActor` where `actorId` matches JWT `sub`
6. `SignalCaptor.waitForSignal()` captures a signal within the timeout (no busy-waiting)
7. `MockCanonicalResolver.getPlayer()` returns a deterministic `PlayerDTO` without HTTP calls
8. `StoragePort` with `fos.storage.provider=noop` returns a non-null `PresignedUploadUrl`

---

## What NOT to Include in This Sprint

- **Redis-backed CanonicalResolver** — ConcurrentHashMap is sufficient for Phase 0; Redis in Phase 1+
- **Real Keycloak integration test** — WireMock simulates JWKS; a real Keycloak test would slow CI significantly
- **Performance tests** — not in Phase 0
- **sdk-test used in fos-gateway tests** — the gateway has no PostgreSQL/MongoDB; its tests use WireMock and Redis only. Do not force gateway tests to extend FosTestContainersBase (it would add unnecessary container startup)

---

## SDK Dependencies Built Across Phase 0

| Module | Sprint | Status |
|--------|--------|--------|
| `sdk-core` | 0.2 | Complete |
| `sdk-events` | 0.2 | Complete |
| `sdk-security` | 0.2 | Complete |
| `sdk-storage` | 0.2 | Complete |
| `sdk-canonical` | 0.3 | Complete |
| `sdk-policy` | 0.4 | Complete |
| `sdk-test` | 0.6 | **Complete this sprint** |

---

## Phase 0 Completion Checklist

Phase 0 is complete and Phase 1 (Workspace) may begin when ALL of the following are true:

- [ ] All SDK modules installed to local Maven (`mvn install` in fos-sdk succeeds)
- [ ] `fos-governance-service` starts on port 8081 with Flyway V001–V005 applied
- [ ] `fos-gateway` starts on port 8080 with valid Keycloak JWKS URI configured
- [ ] `POST /api/v1/actors` → 201 CREATED
- [ ] `POST /api/v1/players` → 201 CREATED
- [ ] `POST /api/v1/teams` → 201 CREATED
- [ ] `POST /api/v1/policy/evaluate` → ALLOW or DENY based on OPA
- [ ] `POST /api/v1/signals` → 202 ACCEPTED
- [ ] `GET /api/v1/players/find` → 200 when found, 404 when not (dedup endpoint)
- [ ] Gateway returns 401 on requests without JWT
- [ ] Gateway returns 429 after rate limit exceeded
- [ ] All integration tests pass: `mvn test -q` in `football-os-core` → BUILD SUCCESS
- [ ] `Phase0SmokeTest` passes all 5 steps
