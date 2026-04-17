# Phase 0 Sprint 0.3 — fos-governance-service: Identity + Canonical + sdk-canonical

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `fos-governance-service` is running with Actor CRUD (create, read, update, deactivate) and a Keycloak webhook sync, plus Player and Team CRUD. `sdk-canonical` is installed to local Maven with `CanonicalRef`, read-only DTOs, and a `CanonicalServiceClient` (Remote Proxy). Kafka FACT signals are emitted on every create/update. An integration test confirms: create actor → Kafka event emitted; create player → `findByIdentity` dedup works.

**Architecture:** `sdk-canonical` is a plain library module in `fos-sdk`. It holds value objects, DTOs, and two proxies — `CanonicalServiceClient` (Remote Proxy over HTTP using `RestClient`) and `CanonicalResolver` (Caching Proxy using `ConcurrentHashMap`). `fos-governance-service` gains two internal packages (`identity` and `canonical`) each owning its own PostgreSQL schema under Flyway control. Both packages emit FACT signals via `FosKafkaProducer` from `sdk-events`. No cross-schema queries — packages communicate only through Kafka or the REST API.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Data JPA, PostgreSQL 16, Flyway 10.x, `sdk-core` (BaseEntity, ResourceState, FosRoles), `sdk-events` (FosKafkaProducer, KafkaTopics), `sdk-security` (FosSecurityContext), Spring RestClient, JUnit 5, Testcontainers

**Required Patterns This Sprint:**
- `[REQUIRED]` **Proxy (Remote)** — `CanonicalServiceClient` wraps HTTP calls; callers never use `RestClient` directly
- `[REQUIRED]` **Proxy (Caching)** — `CanonicalResolver` wraps `CanonicalServiceClient` with a `ConcurrentHashMap` cache
- `[REQUIRED]` **Null Object** — `CanonicalResolver` in test mode returns fixed DTOs (full `MockCanonicalResolver` built in Sprint 0.6; a minimal test stub is defined here)
- `[RECOMMENDED]` **Factory Method** — `ActorFactory` hierarchy: one factory per role type, no `switch(role)` in `ActorService`

---

## File Map

```
fos-sdk/sdk-canonical/
├── pom.xml                                                     CREATE
└── src/
    ├── main/java/com/fos/sdk/canonical/
    │   ├── CanonicalRef.java                                   CREATE
    │   ├── CanonicalType.java                                  CREATE
    │   ├── PlayerDTO.java                                      CREATE
    │   ├── TeamDTO.java                                        CREATE
    │   ├── CanonicalServiceClient.java                         CREATE
    │   ├── CanonicalResolver.java                              CREATE
    │   └── CanonicalAutoConfiguration.java                     CREATE
    └── test/java/com/fos/sdk/canonical/
        └── CanonicalResolverTest.java                          CREATE

fos-sdk/sdk-events/src/main/java/com/fos/sdk/events/
└── KafkaTopics.java                                            MODIFY (add identity + canonical topic constants)

fos-sdk/pom.xml                                                 MODIFY (add sdk-canonical module)

fos-governance-service/
├── pom.xml                                                     MODIFY (add sdk-canonical dependency)
└── src/
    ├── main/
    │   ├── java/com/fos/governance/
    │   │   ├── identity/
    │   │   │   ├── api/
    │   │   │   │   ├── ActorController.java                    CREATE
    │   │   │   │   ├── ActorRequest.java                       CREATE
    │   │   │   │   ├── ActorResponse.java                      CREATE
    │   │   │   │   └── KeycloakWebhookController.java          CREATE
    │   │   │   ├── application/
    │   │   │   │   ├── ActorService.java                       CREATE
    │   │   │   │   └── factory/
    │   │   │   │       ├── ActorFactory.java                   CREATE
    │   │   │   │       ├── PlayerActorFactory.java             CREATE
    │   │   │   │       └── CoachActorFactory.java              CREATE
    │   │   │   ├── domain/
    │   │   │   │   ├── Actor.java                              CREATE
    │   │   │   │   └── ActorRole.java                          CREATE
    │   │   │   └── infrastructure/persistence/
    │   │   │       └── ActorRepository.java                    CREATE
    │   │   ├── canonical/
    │   │   │   ├── api/
    │   │   │   │   ├── PlayerController.java                   CREATE
    │   │   │   │   ├── PlayerRequest.java                      CREATE
    │   │   │   │   ├── TeamController.java                     CREATE
    │   │   │   │   └── TeamRequest.java                        CREATE
    │   │   │   ├── application/
    │   │   │   │   ├── PlayerService.java                      CREATE
    │   │   │   │   └── TeamService.java                        CREATE
    │   │   │   ├── domain/
    │   │   │   │   ├── Player.java                             CREATE
    │   │   │   │   └── Team.java                               CREATE
    │   │   │   └── infrastructure/persistence/
    │   │   │       ├── PlayerRepository.java                   CREATE
    │   │   │       └── TeamRepository.java                     CREATE
    │   │   └── config/
    │   │       └── FlywayConfig.java                           CREATE
    │   └── resources/
    │       └── db/migration/
    │           ├── V001__create_schemas.sql                    CREATE
    │           ├── V002__create_actor_table.sql                CREATE
    │           ├── V003__create_player_table.sql               CREATE
    │           └── V004__create_team_table.sql                 CREATE
    └── test/java/com/fos/governance/
        ├── identity/
        │   └── ActorIntegrationTest.java                       CREATE
        └── canonical/
            └── CanonicalIntegrationTest.java                   CREATE
```

---

## Task 1: Add Identity and Canonical Topic Constants to KafkaTopics

**Files:**
- Modify: `fos-sdk/sdk-events/src/main/java/com/fos/sdk/events/KafkaTopics.java`

- [ ] **Step 1: Add topic constants**

Open `KafkaTopics.java` and append the following blocks. Do not remove existing constants.

```java
// Identity topics
public static final String IDENTITY_ACTOR_CREATED    = "fos.identity.actor.created";
public static final String IDENTITY_ACTOR_UPDATED    = "fos.identity.actor.updated";
public static final String IDENTITY_ACTOR_DEACTIVATED = "fos.identity.actor.deactivated";

// Canonical topics
public static final String CANONICAL_PLAYER_CREATED  = "fos.canonical.player.created";
public static final String CANONICAL_PLAYER_UPDATED  = "fos.canonical.player.updated";
public static final String CANONICAL_TEAM_CREATED    = "fos.canonical.team.created";
public static final String CANONICAL_TEAM_UPDATED    = "fos.canonical.team.updated";

// Audit (consumed by governance signal package in Sprint 0.5)
public static final String AUDIT_ALL                 = "fos.audit.all";
```

- [ ] **Step 2: Verify existing tests still compile**

```bash
cd fos-sdk/sdk-events
mvn test -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add fos-sdk/sdk-events/src/main/java/com/fos/sdk/events/KafkaTopics.java
git commit -m "feat(sdk-events): add identity, canonical, and audit topic constants"
```

---

## Task 2: sdk-canonical — pom.xml + Value Objects + DTOs

**Files:**
- Create: `fos-sdk/sdk-canonical/pom.xml`
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalType.java`
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalRef.java`
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/PlayerDTO.java`
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/TeamDTO.java`

- [ ] **Step 1: Create sdk-canonical pom.xml**

```xml
<!-- fos-sdk/sdk-canonical/pom.xml -->
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

  <artifactId>sdk-canonical</artifactId>
  <name>FOS SDK :: Canonical</name>
  <description>CanonicalRef, DTOs, CanonicalServiceClient, and CanonicalResolver</description>

  <dependencies>
    <!-- sdk-core for BaseEntity types -->
    <dependency>
      <groupId>com.fos</groupId>
      <artifactId>sdk-core</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Spring Web for RestClient -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Boot autoconfigure -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: Create CanonicalType enum**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalType.java
package com.fos.sdk.canonical;

public enum CanonicalType {
    PLAYER,
    TEAM,
    MATCH,
    TRAINING_SESSION,
    CLUB
}
```

- [ ] **Step 3: Create CanonicalRef record**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalRef.java
package com.fos.sdk.canonical;

import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Immutable foreign key to a canonical football entity.
 * Domain entities store this instead of copying canonical fields.
 * To resolve the display name, use CanonicalResolver.getPlayer(ref.id()).
 */
@Embeddable
public record CanonicalRef(CanonicalType type, UUID id) {

    public static CanonicalRef of(CanonicalType type, UUID id) {
        return new CanonicalRef(type, id);
    }

    public static CanonicalRef player(UUID id) {
        return new CanonicalRef(CanonicalType.PLAYER, id);
    }

    public static CanonicalRef team(UUID id) {
        return new CanonicalRef(CanonicalType.TEAM, id);
    }

    public static CanonicalRef club(UUID id) {
        return new CanonicalRef(CanonicalType.CLUB, id);
    }
}
```

- [ ] **Step 4: Create PlayerDTO record**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/PlayerDTO.java
package com.fos.sdk.canonical;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only player identity from fos-governance-service/canonical.
 * Never store these fields in a domain entity — store only a CanonicalRef.
 */
public record PlayerDTO(
    UUID   id,
    String name,
    String position,
    String nationality,
    LocalDate dateOfBirth,
    UUID   currentTeamId
) {}
```

- [ ] **Step 5: Create TeamDTO record**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/TeamDTO.java
package com.fos.sdk.canonical;

import java.util.UUID;

/**
 * Read-only team identity from fos-governance-service/canonical.
 * Never store these fields in a domain entity — store only a CanonicalRef.
 */
public record TeamDTO(
    UUID   id,
    String name,
    String shortName,
    String country,
    UUID   clubId
) {}
```

- [ ] **Step 6: Commit**

```bash
git add fos-sdk/sdk-canonical/
git commit -m "feat(sdk-canonical): add pom, CanonicalRef, CanonicalType, PlayerDTO, TeamDTO"
```

---

## Task 3: sdk-canonical — CanonicalServiceClient (Remote Proxy)

**Files:**
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalServiceClient.java`

- [ ] **Step 1: Write the failing test**

```java
// fos-sdk/sdk-canonical/src/test/java/com/fos/sdk/canonical/CanonicalServiceClientTest.java
package com.fos.sdk.canonical;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(CanonicalServiceClient.class)
class CanonicalServiceClientTest {

    @Autowired
    private CanonicalServiceClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void should_return_player_when_found_by_id() {
        UUID playerId = UUID.randomUUID();
        server.expect(requestTo("/api/v1/players/" + playerId))
              .andRespond(withSuccess("""
                  {"id":"%s","name":"Lionel Test","position":"CF",
                   "nationality":"AR","dateOfBirth":"1990-01-01","currentTeamId":null}
                  """.formatted(playerId), MediaType.APPLICATION_JSON));

        PlayerDTO result = client.getPlayer(playerId);

        assertThat(result.id()).isEqualTo(playerId);
        assertThat(result.name()).isEqualTo("Lionel Test");
    }

    @Test
    void should_return_team_when_found_by_id() {
        UUID teamId = UUID.randomUUID();
        server.expect(requestTo("/api/v1/teams/" + teamId))
              .andRespond(withSuccess("""
                  {"id":"%s","name":"Test FC","shortName":"TFC",
                   "country":"ES","clubId":null}
                  """.formatted(teamId), MediaType.APPLICATION_JSON));

        TeamDTO result = client.getTeam(teamId);

        assertThat(result.id()).isEqualTo(teamId);
        assertThat(result.name()).isEqualTo("Test FC");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-sdk/sdk-canonical
mvn test -Dtest=CanonicalServiceClientTest -q
```

Expected: FAIL — `CanonicalServiceClient` not found

- [ ] **Step 3: Implement CanonicalServiceClient**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalServiceClient.java
package com.fos.sdk.canonical;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Remote Proxy for canonical football entity reads.
 * Hides the HTTP call to fos-governance-service from all domain code.
 * Prefer CanonicalResolver (caching proxy) over this class in domain services.
 */
@Component
public class CanonicalServiceClient {

    private final RestClient restClient;

    public CanonicalServiceClient(
            RestClient.Builder builder,
            @Value("${fos.canonical.service-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public PlayerDTO getPlayer(UUID id) {
        return restClient.get()
                .uri("/api/v1/players/{id}", id)
                .retrieve()
                .body(PlayerDTO.class);
    }

    public TeamDTO getTeam(UUID id) {
        return restClient.get()
                .uri("/api/v1/teams/{id}", id)
                .retrieve()
                .body(TeamDTO.class);
    }

    /**
     * Used by fos-ingest-service to deduplicate players before creating new canonical entries.
     */
    public Optional<PlayerDTO> findByIdentity(String name, LocalDate dob, String nationality) {
        try {
            PlayerDTO result = restClient.get()
                    .uri(b -> b.path("/api/v1/players/find")
                                .queryParam("name", name)
                                .queryParam("dob", dob)
                                .queryParam("nationality", nationality)
                                .build())
                    .retrieve()
                    .body(PlayerDTO.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Used by fos-ingest-service to deduplicate teams before creating new canonical entries.
     */
    public Optional<TeamDTO> findTeamByName(String name, String country) {
        try {
            TeamDTO result = restClient.get()
                    .uri(b -> b.path("/api/v1/teams/find")
                                .queryParam("name", name)
                                .queryParam("country", country)
                                .build())
                    .retrieve()
                    .body(TeamDTO.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-sdk/sdk-canonical
mvn test -Dtest=CanonicalServiceClientTest -q
```

Expected: BUILD SUCCESS — 2 tests pass

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-canonical/src/
git commit -m "feat(sdk-canonical): add CanonicalServiceClient remote proxy"
```

---

## Task 4: sdk-canonical — CanonicalResolver (Caching Proxy)

**Files:**
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalResolver.java`
- Create: `fos-sdk/sdk-canonical/src/test/java/com/fos/sdk/canonical/CanonicalResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
// fos-sdk/sdk-canonical/src/test/java/com/fos/sdk/canonical/CanonicalResolverTest.java
package com.fos.sdk.canonical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CanonicalResolverTest {

    private CanonicalServiceClient mockClient;
    private CanonicalResolver resolver;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(CanonicalServiceClient.class);
        resolver = new CanonicalResolver(mockClient);
    }

    @Test
    void should_return_player_from_client_on_first_call() {
        UUID id = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(id, "Test Player", "ST", "ES",
                java.time.LocalDate.of(1995, 5, 5), null);
        when(mockClient.getPlayer(id)).thenReturn(dto);

        PlayerDTO result = resolver.getPlayer(id);

        assertThat(result.name()).isEqualTo("Test Player");
        verify(mockClient, times(1)).getPlayer(id);
    }

    @Test
    void should_return_player_from_cache_on_second_call() {
        UUID id = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(id, "Cached Player", "CM", "BR",
                java.time.LocalDate.of(1998, 3, 3), null);
        when(mockClient.getPlayer(id)).thenReturn(dto);

        resolver.getPlayer(id); // populates cache
        resolver.getPlayer(id); // should hit cache

        // client called only once despite two resolver calls
        verify(mockClient, times(1)).getPlayer(id);
    }

    @Test
    void should_evict_player_cache_entry() {
        UUID id = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(id, "Evict Player", "GK", "DE",
                java.time.LocalDate.of(2000, 1, 1), null);
        when(mockClient.getPlayer(id)).thenReturn(dto);

        resolver.getPlayer(id);
        resolver.evict(CanonicalRef.player(id));
        resolver.getPlayer(id); // must call client again after eviction

        verify(mockClient, times(2)).getPlayer(id);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-sdk/sdk-canonical
mvn test -Dtest=CanonicalResolverTest -q
```

Expected: FAIL — `CanonicalResolver` not found

- [ ] **Step 3: Implement CanonicalResolver**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalResolver.java
package com.fos.sdk.canonical;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching Proxy over CanonicalServiceClient.
 * Domain services inject CanonicalResolver — never CanonicalServiceClient directly.
 *
 * Cache is in-memory (ConcurrentHashMap). Redis-backed caching is added in Phase 1+
 * when canonical resolution becomes a measured bottleneck.
 *
 * Cache entries are evicted explicitly (on FACT signals from governance) or on-demand.
 * There is no TTL here — canonical data rarely changes.
 */
@Component
public class CanonicalResolver {

    private final CanonicalServiceClient client;
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public CanonicalResolver(CanonicalServiceClient client) {
        this.client = client;
    }

    public PlayerDTO getPlayer(UUID id) {
        String key = "player:" + id;
        return (PlayerDTO) cache.computeIfAbsent(key, k -> client.getPlayer(id));
    }

    public TeamDTO getTeam(UUID id) {
        String key = "team:" + id;
        return (TeamDTO) cache.computeIfAbsent(key, k -> client.getTeam(id));
    }

    /**
     * Evict a cache entry when a FACT signal indicates the canonical entity was updated.
     * Called by domain services that consume canonical FACT signals.
     */
    public void evict(CanonicalRef ref) {
        String key = ref.type().name().toLowerCase() + ":" + ref.id();
        cache.remove(key);
    }

    /** Evict all entries. Useful for tests. */
    public void evictAll() {
        cache.clear();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd fos-sdk/sdk-canonical
mvn test -Dtest=CanonicalResolverTest -q
```

Expected: BUILD SUCCESS — 3 tests pass

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-canonical/src/
git commit -m "feat(sdk-canonical): add CanonicalResolver caching proxy"
```

---

## Task 5: sdk-canonical — Auto-Configuration + SDK Aggregator Registration

**Files:**
- Create: `fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalAutoConfiguration.java`
- Create: `fos-sdk/sdk-canonical/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `fos-sdk/pom.xml`

- [ ] **Step 1: Create CanonicalAutoConfiguration**

```java
// fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalAutoConfiguration.java
package com.fos.sdk.canonical;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class CanonicalAutoConfiguration {

    @Bean
    public CanonicalServiceClient canonicalServiceClient(RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value(
                "${fos.canonical.service-url:http://localhost:8081}") String baseUrl) {
        return new CanonicalServiceClient(builder, baseUrl);
    }

    @Bean
    public CanonicalResolver canonicalResolver(CanonicalServiceClient client) {
        return new CanonicalResolver(client);
    }
}
```

- [ ] **Step 2: Register auto-configuration**

```
# fos-sdk/sdk-canonical/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.fos.sdk.canonical.CanonicalAutoConfiguration
```

- [ ] **Step 3: Add sdk-canonical to SDK aggregator pom.xml**

In `fos-sdk/pom.xml`, add `<module>sdk-canonical</module>` to the `<modules>` section. Position it after `sdk-storage` and before `sdk-policy`, following the build order:

```xml
<modules>
  <module>sdk-core</module>
  <module>sdk-events</module>
  <module>sdk-security</module>
  <module>sdk-storage</module>
  <module>sdk-canonical</module>   <!-- add this line -->
  <module>sdk-policy</module>
  <module>sdk-test</module>
</modules>
```

- [ ] **Step 4: Build and install sdk-canonical**

```bash
cd fos-sdk
mvn install -pl sdk-canonical -am -q
```

Expected: BUILD SUCCESS — sdk-canonical-1.0.0-SNAPSHOT installed to local Maven repository

- [ ] **Step 5: Commit**

```bash
git add fos-sdk/sdk-canonical/src/main/resources/ \
        fos-sdk/sdk-canonical/src/main/java/com/fos/sdk/canonical/CanonicalAutoConfiguration.java \
        fos-sdk/pom.xml
git commit -m "feat(sdk-canonical): add auto-configuration and register as SDK module"
```

---

## Task 6: fos-governance-service — Flyway Migrations

**Files:**
- Modify: `fos-governance-service/pom.xml`
- Create: `fos-governance-service/src/main/resources/db/migration/V001__create_schemas.sql`
- Create: `fos-governance-service/src/main/resources/db/migration/V002__create_actor_table.sql`
- Create: `fos-governance-service/src/main/resources/db/migration/V003__create_player_table.sql`
- Create: `fos-governance-service/src/main/resources/db/migration/V004__create_team_table.sql`

- [ ] **Step 1: Add sdk-canonical dependency to governance pom.xml**

In `fos-governance-service/pom.xml`, add inside `<dependencies>`:

```xml
<dependency>
  <groupId>com.fos</groupId>
  <artifactId>sdk-canonical</artifactId>
  <version>${fos.sdk.version}</version>
</dependency>
```

Also verify these dependencies are already present (from Sprint 0.1 skeleton setup):

```xml
<!-- sdk-core, sdk-events, sdk-security should already be listed -->
<dependency>
  <groupId>com.fos</groupId>
  <artifactId>sdk-core</artifactId>
  <version>${fos.sdk.version}</version>
</dependency>
<dependency>
  <groupId>com.fos</groupId>
  <artifactId>sdk-events</artifactId>
  <version>${fos.sdk.version}</version>
</dependency>
<dependency>
  <groupId>com.fos</groupId>
  <artifactId>sdk-security</artifactId>
  <version>${fos.sdk.version}</version>
</dependency>

<!-- JPA + PostgreSQL + Flyway -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

- [ ] **Step 2: Create V001__create_schemas.sql**

```sql
-- fos-governance-service/src/main/resources/db/migration/V001__create_schemas.sql
-- Creates all schemas that fos-governance-service owns.
-- Each package (identity, canonical, policy, signal, audit) owns its schema exclusively.

CREATE SCHEMA IF NOT EXISTS fos_identity;
CREATE SCHEMA IF NOT EXISTS fos_canonical;
CREATE SCHEMA IF NOT EXISTS fos_policy;
CREATE SCHEMA IF NOT EXISTS fos_signal;
CREATE SCHEMA IF NOT EXISTS fos_audit;
```

- [ ] **Step 3: Create V002__create_actor_table.sql**

```sql
-- fos-governance-service/src/main/resources/db/migration/V002__create_actor_table.sql

CREATE TABLE fos_identity.actor (
    resource_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id  VARCHAR(255) UNIQUE,
    email             VARCHAR(255) NOT NULL UNIQUE,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    role              VARCHAR(50)  NOT NULL,
    state             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    club_id           UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_actor_keycloak_user_id ON fos_identity.actor(keycloak_user_id);
CREATE INDEX idx_actor_email            ON fos_identity.actor(email);
CREATE INDEX idx_actor_state            ON fos_identity.actor(state);
```

- [ ] **Step 4: Create V003__create_player_table.sql**

```sql
-- fos-governance-service/src/main/resources/db/migration/V003__create_player_table.sql

CREATE TABLE fos_canonical.player (
    player_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    position         VARCHAR(10),
    nationality      VARCHAR(100),
    date_of_birth    DATE,
    current_team_id  UUID,
    state            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0
);

-- Unique constraint used for deduplication by fos-ingest-service
CREATE UNIQUE INDEX uidx_player_identity
    ON fos_canonical.player(name, date_of_birth, nationality)
    WHERE date_of_birth IS NOT NULL AND nationality IS NOT NULL;

CREATE INDEX idx_player_name    ON fos_canonical.player(lower(name));
CREATE INDEX idx_player_team_id ON fos_canonical.player(current_team_id);
```

- [ ] **Step 5: Create V004__create_team_table.sql**

```sql
-- fos-governance-service/src/main/resources/db/migration/V004__create_team_table.sql

CREATE TABLE fos_canonical.team (
    team_id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    short_name  VARCHAR(10),
    country     VARCHAR(100),
    club_id     UUID,
    state       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uidx_team_name_country ON fos_canonical.team(lower(name), lower(country));
CREATE INDEX idx_team_club_id ON fos_canonical.team(club_id);
```

- [ ] **Step 6: Add Flyway configuration for multi-schema**

Create `fos-governance-service/src/main/resources/application.yml` entries (or update if it exists):

```yaml
spring:
  datasource:
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/fos_governance}
    username: ${POSTGRES_USER:fos}
    password: ${POSTGRES_PASSWORD:fos}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: fos_identity   # JPA default; each entity specifies its own schema via @Table
  flyway:
    enabled: true
    locations: classpath:db/migration
    default-schema: public             # Flyway tracks its history in public.flyway_schema_history

fos:
  canonical:
    service-url: ${CANONICAL_SERVICE_URL:http://localhost:8081}
```

- [ ] **Step 7: Commit**

```bash
git add fos-governance-service/pom.xml \
        fos-governance-service/src/main/resources/
git commit -m "feat(governance): add Flyway migrations for identity, canonical, policy, signal, audit schemas"
```

---

## Task 7: Identity Package — Actor Domain Entity + Repository

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/domain/ActorRole.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/domain/Actor.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/infrastructure/persistence/ActorRepository.java`

- [ ] **Step 1: Create ActorRole enum**

```java
// ActorRole.java
package com.fos.governance.identity.domain;

/**
 * All valid actor roles in Football OS.
 * Values must match FosRoles constants in sdk-core.
 */
public enum ActorRole {
    PLAYER,
    HEAD_COACH,
    ASSISTANT_COACH,
    GOALKEEPER_COACH,
    PHYSICAL_TRAINER,
    MEDICAL_STAFF,
    ANALYST,
    CLUB_ADMIN,
    OPERATOR
}
```

- [ ] **Step 2: Create Actor entity**

```java
// Actor.java
package com.fos.governance.identity.domain;

import com.fos.sdk.core.ResourceState;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * An Actor is any human user of Football OS: player, coach, admin, analyst.
 * Identity is owned by Keycloak; this entity mirrors what we need for authorization and signals.
 * Actor belongs to the fos_identity schema — not accessible from domain services.
 */
@Entity
@Table(schema = "fos_identity", name = "actor")
@EntityListeners(AuditingEntityListener.class)
public class Actor {

    @Id
    @Column(name = "resource_id")
    private UUID resourceId = UUID.randomUUID();

    @Column(name = "keycloak_user_id", unique = true)
    private String keycloakUserId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ActorRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ResourceState state = ResourceState.DRAFT;

    @Column(name = "club_id")
    private UUID clubId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    protected Actor() {}

    public Actor(String email, String firstName, String lastName, ActorRole role, UUID clubId) {
        this.resourceId = UUID.randomUUID();
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.clubId = clubId;
        this.state = ResourceState.DRAFT;
    }

    public void activate() {
        if (this.state != ResourceState.DRAFT) {
            throw new IllegalStateException("Only DRAFT actors can be activated");
        }
        this.state = ResourceState.ACTIVE;
    }

    public void deactivate() {
        if (this.state == ResourceState.ARCHIVED) {
            throw new IllegalStateException("Already deactivated");
        }
        this.state = ResourceState.ARCHIVED;
    }

    public void syncKeycloakId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
        if (this.state == ResourceState.DRAFT) {
            this.state = ResourceState.ACTIVE;
        }
    }

    // Getters — no setters; mutations go through domain methods above
    public UUID getResourceId()       { return resourceId; }
    public String getKeycloakUserId() { return keycloakUserId; }
    public String getEmail()          { return email; }
    public String getFirstName()      { return firstName; }
    public String getLastName()       { return lastName; }
    public ActorRole getRole()        { return role; }
    public ResourceState getState()   { return state; }
    public UUID getClubId()           { return clubId; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
    public Long getVersion()          { return version; }
}
```

- [ ] **Step 3: Create ActorRepository**

```java
// ActorRepository.java
package com.fos.governance.identity.infrastructure.persistence;

import com.fos.governance.identity.domain.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActorRepository extends JpaRepository<Actor, UUID> {
    Optional<Actor> findByEmail(String email);
    Optional<Actor> findByKeycloakUserId(String keycloakUserId);
}
```

- [ ] **Step 4: Enable JPA auditing in GovernanceApp**

Add `@EnableJpaAuditing` to `GovernanceApp.java`:

```java
// com/fos/governance/GovernanceApp.java
package com.fos.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class GovernanceApp {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceApp.class, args);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/identity/ \
        fos-governance-service/src/main/java/com/fos/governance/GovernanceApp.java
git commit -m "feat(governance/identity): add Actor entity, ActorRole, ActorRepository"
```

---

## Task 8: Identity Package — ActorFactory (Factory Method Pattern)

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/application/factory/ActorFactory.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/application/factory/PlayerActorFactory.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/application/factory/CoachActorFactory.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/application/factory/DefaultActorFactory.java`

- [ ] **Step 1: Write the failing test**

```java
// fos-governance-service/src/test/java/com/fos/governance/identity/ActorFactoryTest.java
package com.fos.governance.identity;

import com.fos.governance.identity.application.factory.CoachActorFactory;
import com.fos.governance.identity.application.factory.PlayerActorFactory;
import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.sdk.core.ResourceState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActorFactoryTest {

    @Test
    void should_create_player_actor_with_draft_state() {
        PlayerActorFactory factory = new PlayerActorFactory();
        Actor actor = factory.create("player@club.com", "Carlos", "Silva", UUID.randomUUID());

        assertThat(actor.getRole()).isEqualTo(ActorRole.PLAYER);
        assertThat(actor.getState()).isEqualTo(ResourceState.DRAFT);
        assertThat(actor.getEmail()).isEqualTo("player@club.com");
    }

    @Test
    void should_create_coach_actor_with_draft_state() {
        CoachActorFactory factory = new CoachActorFactory();
        Actor actor = factory.create("coach@club.com", "Marco", "Rossi", UUID.randomUUID());

        assertThat(actor.getRole()).isEqualTo(ActorRole.HEAD_COACH);
        assertThat(actor.getState()).isEqualTo(ResourceState.DRAFT);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd fos-governance-service
mvn test -Dtest=ActorFactoryTest -q
```

Expected: FAIL — `PlayerActorFactory` not found

- [ ] **Step 3: Implement ActorFactory hierarchy**

```java
// ActorFactory.java
package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.Actor;
import java.util.UUID;

/**
 * Factory Method base. Each actor type subclass knows the correct default role
 * and any role-specific initialization. ActorService selects the right factory
 * via ActorFactoryRegistry — no switch(role) in service code.
 */
public abstract class ActorFactory {

    public final Actor create(String email, String firstName, String lastName, UUID clubId) {
        Actor actor = new Actor(email, firstName, lastName, defaultRole(), clubId);
        postCreate(actor);
        return actor;
    }

    protected abstract com.fos.governance.identity.domain.ActorRole defaultRole();

    /** Hook for role-specific initialization. Default: no-op. */
    protected void postCreate(Actor actor) {}
}
```

```java
// PlayerActorFactory.java
package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;

public class PlayerActorFactory extends ActorFactory {
    @Override
    protected ActorRole defaultRole() { return ActorRole.PLAYER; }
}
```

```java
// CoachActorFactory.java
package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;

public class CoachActorFactory extends ActorFactory {
    @Override
    protected ActorRole defaultRole() { return ActorRole.HEAD_COACH; }
}
```

```java
// DefaultActorFactory.java — fallback for roles without specialized factories
package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;

public class DefaultActorFactory extends ActorFactory {

    private final ActorRole role;

    public DefaultActorFactory(ActorRole role) {
        this.role = role;
    }

    @Override
    protected ActorRole defaultRole() { return role; }
}
```

- [ ] **Step 4: Create ActorFactoryRegistry (eliminates switch in service)**

```java
// fos-governance-service/src/main/java/com/fos/governance/identity/application/factory/ActorFactoryRegistry.java
package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class ActorFactoryRegistry {

    private final Map<ActorRole, ActorFactory> factories = new EnumMap<>(ActorRole.class);

    public ActorFactoryRegistry() {
        factories.put(ActorRole.PLAYER, new PlayerActorFactory());
        factories.put(ActorRole.HEAD_COACH, new CoachActorFactory());
        // All other roles use DefaultActorFactory
        for (ActorRole role : ActorRole.values()) {
            factories.computeIfAbsent(role, r -> new DefaultActorFactory(r));
        }
    }

    public ActorFactory forRole(ActorRole role) {
        return factories.get(role);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd fos-governance-service
mvn test -Dtest=ActorFactoryTest -q
```

Expected: BUILD SUCCESS — 2 tests pass

- [ ] **Step 6: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/identity/application/factory/ \
        fos-governance-service/src/test/java/com/fos/governance/identity/ActorFactoryTest.java
git commit -m "feat(governance/identity): add ActorFactory hierarchy and ActorFactoryRegistry"
```

---

## Task 9: Identity Package — ActorService + ActorController + KeycloakWebhookController

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/api/ActorRequest.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/api/ActorResponse.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/application/ActorService.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/api/ActorController.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/identity/api/KeycloakWebhookController.java`

- [ ] **Step 1: Create request/response records**

```java
// ActorRequest.java
package com.fos.governance.identity.api;

import com.fos.governance.identity.domain.ActorRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActorRequest(
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull ActorRole role,
    UUID clubId
) {}
```

```java
// ActorResponse.java
package com.fos.governance.identity.api;

import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.sdk.core.ResourceState;

import java.time.Instant;
import java.util.UUID;

public record ActorResponse(
    UUID resourceId,
    String email,
    String firstName,
    String lastName,
    ActorRole role,
    ResourceState state,
    UUID clubId,
    Instant createdAt
) {
    public static ActorResponse from(Actor a) {
        return new ActorResponse(
            a.getResourceId(), a.getEmail(), a.getFirstName(), a.getLastName(),
            a.getRole(), a.getState(), a.getClubId(), a.getCreatedAt()
        );
    }
}
```

- [ ] **Step 2: Implement ActorService with FACT signal emission**

```java
// ActorService.java
package com.fos.governance.identity.application;

import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.application.factory.ActorFactoryRegistry;
import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.infrastructure.persistence.ActorRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ActorService {

    private final ActorRepository actorRepository;
    private final ActorFactoryRegistry factoryRegistry;
    private final FosKafkaProducer kafkaProducer;

    public ActorService(ActorRepository actorRepository,
                        ActorFactoryRegistry factoryRegistry,
                        FosKafkaProducer kafkaProducer) {
        this.actorRepository = actorRepository;
        this.factoryRegistry = factoryRegistry;
        this.kafkaProducer = kafkaProducer;
    }

    public Actor createActor(ActorRequest request) {
        Actor actor = factoryRegistry
                .forRole(request.role())
                .create(request.email(), request.firstName(), request.lastName(), request.clubId());

        Actor saved = actorRepository.save(actor);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB,
                        request.clubId() != null ? request.clubId() : UUID.randomUUID()))
                .payload(Map.of(
                        "actorId",    saved.getResourceId().toString(),
                        "email",      saved.getEmail(),
                        "role",       saved.getRole().name(),
                        "state",      saved.getState().name()
                ))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Actor getActor(UUID id) {
        return actorRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Actor not found: " + id));
    }

    public Actor deactivateActor(UUID id) {
        Actor actor = getActor(id);
        actor.deactivate();
        Actor saved = actorRepository.save(actor);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.IDENTITY_ACTOR_DEACTIVATED)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, UUID.randomUUID()))
                .payload(Map.of("actorId", saved.getResourceId().toString()))
                .build());

        return saved;
    }

    /** Called by KeycloakWebhookController when Keycloak fires a REGISTER event. */
    public void syncKeycloakUser(String keycloakUserId, String email) {
        actorRepository.findByEmail(email).ifPresent(actor -> {
            actor.syncKeycloakId(keycloakUserId);
            actorRepository.save(actor);
        });
    }
}
```

- [ ] **Step 3: Implement ActorController**

```java
// ActorController.java
package com.fos.governance.identity.api;

import com.fos.governance.identity.application.ActorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/actors")
public class ActorController {

    private final ActorService actorService;

    public ActorController(ActorService actorService) {
        this.actorService = actorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActorResponse createActor(@Valid @RequestBody ActorRequest request) {
        return ActorResponse.from(actorService.createActor(request));
    }

    @GetMapping("/{id}")
    public ActorResponse getActor(@PathVariable UUID id) {
        return ActorResponse.from(actorService.getActor(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateActor(@PathVariable UUID id) {
        actorService.deactivateActor(id);
    }
}
```

- [ ] **Step 4: Implement KeycloakWebhookController**

Keycloak calls this endpoint when a user registers. The identity package uses it to link the Keycloak user ID to the existing `Actor` record.

```java
// KeycloakWebhookController.java
package com.fos.governance.identity.api;

import com.fos.governance.identity.application.ActorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives Keycloak Admin Events and User Events via webhook.
 * Configure Keycloak to send to: POST /api/v1/identity/keycloak/webhook
 *
 * Expected payload (Keycloak event format):
 * {
 *   "type":    "REGISTER",
 *   "userId":  "kc-uuid-string",
 *   "details": { "email": "actor@club.com" }
 * }
 */
@RestController
@RequestMapping("/api/v1/identity/keycloak")
public class KeycloakWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KeycloakWebhookController.class);

    private final ActorService actorService;

    public KeycloakWebhookController(ActorService actorService) {
        this.actorService = actorService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleEvent(@RequestBody Map<String, Object> event) {
        String type = (String) event.get("type");
        if ("REGISTER".equals(type) || "LOGIN".equals(type)) {
            String keycloakUserId = (String) event.get("userId");
            @SuppressWarnings("unchecked")
            Map<String, String> details = (Map<String, String>) event.getOrDefault("details", Map.of());
            String email = details.get("email");
            if (keycloakUserId != null && email != null) {
                actorService.syncKeycloakUser(keycloakUserId, email);
            } else {
                log.warn("Keycloak webhook missing userId or email for event type={}", type);
            }
        }
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/identity/
git commit -m "feat(governance/identity): add ActorService, ActorController, KeycloakWebhookController with FACT signal emission"
```

---

## Task 10: Canonical Package — Player + Team Entities + Repositories

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/domain/Player.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/domain/Team.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/infrastructure/persistence/PlayerRepository.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/infrastructure/persistence/TeamRepository.java`

- [ ] **Step 1: Create Player entity**

```java
// Player.java
package com.fos.governance.canonical.domain;

import com.fos.sdk.core.ResourceState;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Canonical identity fact for a football player.
 * Stores only identity fields — no stats, no documents, no domain-specific data.
 * Domain services reference players via CanonicalRef; they do NOT import this class.
 */
@Entity
@Table(schema = "fos_canonical", name = "player")
@EntityListeners(AuditingEntityListener.class)
public class Player {

    @Id
    @Column(name = "player_id")
    private UUID playerId = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "position")
    private String position;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "current_team_id")
    private UUID currentTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ResourceState state = ResourceState.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    protected Player() {}

    public Player(String name, String position, String nationality,
                  LocalDate dateOfBirth, UUID currentTeamId) {
        this.playerId = UUID.randomUUID();
        this.name = name;
        this.position = position;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
        this.currentTeamId = currentTeamId;
        this.state = ResourceState.ACTIVE;
    }

    public void updateTeam(UUID teamId) {
        this.currentTeamId = teamId;
    }

    // Getters
    public UUID getPlayerId()         { return playerId; }
    public String getName()           { return name; }
    public String getPosition()       { return position; }
    public String getNationality()    { return nationality; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public UUID getCurrentTeamId()    { return currentTeamId; }
    public ResourceState getState()   { return state; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
    public Long getVersion()          { return version; }
}
```

- [ ] **Step 2: Create Team entity**

```java
// Team.java
package com.fos.governance.canonical.domain;

import com.fos.sdk.core.ResourceState;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "fos_canonical", name = "team")
@EntityListeners(AuditingEntityListener.class)
public class Team {

    @Id
    @Column(name = "team_id")
    private UUID teamId = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "country")
    private String country;

    @Column(name = "club_id")
    private UUID clubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ResourceState state = ResourceState.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    protected Team() {}

    public Team(String name, String shortName, String country, UUID clubId) {
        this.teamId = UUID.randomUUID();
        this.name = name;
        this.shortName = shortName;
        this.country = country;
        this.clubId = clubId;
        this.state = ResourceState.ACTIVE;
    }

    // Getters
    public UUID getTeamId()         { return teamId; }
    public String getName()         { return name; }
    public String getShortName()    { return shortName; }
    public String getCountry()      { return country; }
    public UUID getClubId()         { return clubId; }
    public ResourceState getState() { return state; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
    public Long getVersion()        { return version; }
}
```

- [ ] **Step 3: Create PlayerRepository**

```java
// PlayerRepository.java
package com.fos.governance.canonical.infrastructure.persistence;

import com.fos.governance.canonical.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    /**
     * Used by fos-ingest-service (via CanonicalServiceClient) to dedup before creating a new Player.
     */
    Optional<Player> findByNameAndDateOfBirthAndNationality(
            String name, LocalDate dateOfBirth, String nationality);

    boolean existsByNameAndDateOfBirthAndNationality(
            String name, LocalDate dateOfBirth, String nationality);
}
```

- [ ] **Step 4: Create TeamRepository**

```java
// TeamRepository.java
package com.fos.governance.canonical.infrastructure.persistence;

import com.fos.governance.canonical.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findByNameIgnoreCaseAndCountryIgnoreCase(String name, String country);
}
```

- [ ] **Step 5: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/canonical/
git commit -m "feat(governance/canonical): add Player, Team entities and Spring Data repositories"
```

---

## Task 11: Canonical Package — Services + Controllers

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/api/PlayerRequest.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/application/PlayerService.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/api/PlayerController.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/api/TeamRequest.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/application/TeamService.java`
- Create: `fos-governance-service/src/main/java/com/fos/governance/canonical/api/TeamController.java`

- [ ] **Step 1: Create PlayerRequest record**

```java
// PlayerRequest.java
package com.fos.governance.canonical.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

public record PlayerRequest(
    @NotBlank String name,
    String position,
    String nationality,
    LocalDate dateOfBirth,
    UUID currentTeamId
) {}
```

- [ ] **Step 2: Implement PlayerService with FACT signals and dedup**

```java
// PlayerService.java
package com.fos.governance.canonical.application;

import com.fos.governance.canonical.api.PlayerRequest;
import com.fos.governance.canonical.domain.Player;
import com.fos.governance.canonical.infrastructure.persistence.PlayerRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final FosKafkaProducer kafkaProducer;

    public PlayerService(PlayerRepository playerRepository, FosKafkaProducer kafkaProducer) {
        this.playerRepository = playerRepository;
        this.kafkaProducer = kafkaProducer;
    }

    public Player createPlayer(PlayerRequest request) {
        Player player = new Player(
                request.name(), request.position(),
                request.nationality(), request.dateOfBirth(), request.currentTeamId());

        Player saved = playerRepository.save(player);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.CANONICAL_PLAYER_CREATED)
                .actorRef(CanonicalRef.of(CanonicalType.PLAYER, saved.getPlayerId()))
                .payload(Map.of(
                        "playerId",    saved.getPlayerId().toString(),
                        "name",        saved.getName(),
                        "position",    saved.getPosition() != null ? saved.getPosition() : "",
                        "nationality", saved.getNationality() != null ? saved.getNationality() : ""
                ))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Player getPlayer(UUID id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found: " + id));
    }

    /**
     * Dedup check used by fos-ingest-service via CanonicalServiceClient.
     * Returns the existing player if (name + dob + nationality) matches.
     */
    @Transactional(readOnly = true)
    public Optional<Player> findByIdentity(String name,
                                           java.time.LocalDate dob,
                                           String nationality) {
        return playerRepository.findByNameAndDateOfBirthAndNationality(name, dob, nationality);
    }

    /** Maps domain entity to the DTO that sdk-canonical callers expect. */
    public static PlayerDTO toDTO(Player p) {
        return new PlayerDTO(
                p.getPlayerId(), p.getName(), p.getPosition(),
                p.getNationality(), p.getDateOfBirth(), p.getCurrentTeamId());
    }
}
```

- [ ] **Step 3: Implement PlayerController**

```java
// PlayerController.java
package com.fos.governance.canonical.api;

import com.fos.governance.canonical.application.PlayerService;
import com.fos.sdk.canonical.PlayerDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerDTO createPlayer(@Valid @RequestBody PlayerRequest request) {
        return PlayerService.toDTO(playerService.createPlayer(request));
    }

    @GetMapping("/{id}")
    public PlayerDTO getPlayer(@PathVariable UUID id) {
        return PlayerService.toDTO(playerService.getPlayer(id));
    }

    /**
     * Deduplication endpoint for fos-ingest-service.
     * Returns 200 with body if found, 404 if not.
     * CanonicalServiceClient.findByIdentity() wraps this call.
     */
    @GetMapping("/find")
    public PlayerDTO findByIdentity(
            @RequestParam String name,
            @RequestParam LocalDate dob,
            @RequestParam String nationality) {
        return playerService.findByIdentity(name, dob, nationality)
                .map(PlayerService::toDTO)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Player not found: " + name));
    }
}
```

- [ ] **Step 4: Create TeamRequest and TeamService**

```java
// TeamRequest.java
package com.fos.governance.canonical.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record TeamRequest(
    @NotBlank String name,
    String shortName,
    String country,
    UUID clubId
) {}
```

```java
// TeamService.java
package com.fos.governance.canonical.application;

import com.fos.governance.canonical.api.TeamRequest;
import com.fos.governance.canonical.domain.Team;
import com.fos.governance.canonical.infrastructure.persistence.TeamRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.TeamDTO;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final FosKafkaProducer kafkaProducer;

    public TeamService(TeamRepository teamRepository, FosKafkaProducer kafkaProducer) {
        this.teamRepository = teamRepository;
        this.kafkaProducer = kafkaProducer;
    }

    public Team createTeam(TeamRequest request) {
        Team team = new Team(request.name(), request.shortName(),
                             request.country(), request.clubId());
        Team saved = teamRepository.save(team);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.CANONICAL_TEAM_CREATED)
                .actorRef(CanonicalRef.of(CanonicalType.TEAM, saved.getTeamId()))
                .payload(Map.of(
                        "teamId",  saved.getTeamId().toString(),
                        "name",    saved.getName(),
                        "country", saved.getCountry() != null ? saved.getCountry() : ""
                ))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Team getTeam(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Team> findByName(String name, String country) {
        return teamRepository.findByNameIgnoreCaseAndCountryIgnoreCase(name, country);
    }

    public static TeamDTO toDTO(Team t) {
        return new TeamDTO(t.getTeamId(), t.getName(), t.getShortName(),
                           t.getCountry(), t.getClubId());
    }
}
```

- [ ] **Step 5: Implement TeamController**

```java
// TeamController.java
package com.fos.governance.canonical.api;

import com.fos.governance.canonical.application.TeamService;
import com.fos.sdk.canonical.TeamDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDTO createTeam(@Valid @RequestBody TeamRequest request) {
        return TeamService.toDTO(teamService.createTeam(request));
    }

    @GetMapping("/{id}")
    public TeamDTO getTeam(@PathVariable UUID id) {
        return TeamService.toDTO(teamService.getTeam(id));
    }

    @GetMapping("/find")
    public TeamDTO findByName(
            @RequestParam String name,
            @RequestParam String country) {
        return teamService.findByName(name, country)
                .map(TeamService::toDTO)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Team not found: " + name + " / " + country));
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/canonical/
git commit -m "feat(governance/canonical): add PlayerService, TeamService, PlayerController, TeamController with FACT signal emission"
```

---

## Task 12: Integration Tests

**Files:**
- Create: `fos-governance-service/src/test/java/com/fos/governance/identity/ActorIntegrationTest.java`
- Create: `fos-governance-service/src/test/java/com/fos/governance/canonical/CanonicalIntegrationTest.java`

These tests use Testcontainers directly (before `sdk-test` is available in Sprint 0.6). PostgreSQL and Kafka containers are started inline.

- [ ] **Step 1: Add test dependencies to governance pom.xml**

In `fos-governance-service/pom.xml`, ensure these test dependencies are present:

```xml
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
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <version>1.19.8</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>kafka</artifactId>
  <version>1.19.8</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write ActorIntegrationTest**

```java
// ActorIntegrationTest.java
package com.fos.governance.identity;

import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.api.ActorResponse;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.sdk.core.ResourceState;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fos_governance");

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
    void should_create_actor_and_return_201() {
        ActorRequest request = new ActorRequest(
                "player@testclub.com", "Carlos", "Silva",
                ActorRole.PLAYER, UUID.randomUUID());

        ResponseEntity<ActorResponse> response = restTemplate.postForEntity(
                "/api/v1/actors", request, ActorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("player@testclub.com");
        assertThat(response.getBody().role()).isEqualTo(ActorRole.PLAYER);
        assertThat(response.getBody().state()).isEqualTo(ResourceState.DRAFT);
    }

    @Test
    void should_return_actor_by_id() {
        ActorRequest request = new ActorRequest(
                "coach@testclub.com", "Marco", "Rossi",
                ActorRole.HEAD_COACH, UUID.randomUUID());

        ActorResponse created = restTemplate.postForObject(
                "/api/v1/actors", request, ActorResponse.class);

        ResponseEntity<ActorResponse> fetched = restTemplate.getForEntity(
                "/api/v1/actors/" + created.resourceId(), ActorResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().resourceId()).isEqualTo(created.resourceId());
    }

    @Test
    void should_deactivate_actor() {
        ActorRequest request = new ActorRequest(
                "admin@testclub.com", "Anna", "Klein",
                ActorRole.CLUB_ADMIN, UUID.randomUUID());

        ActorResponse created = restTemplate.postForObject(
                "/api/v1/actors", request, ActorResponse.class);

        restTemplate.delete("/api/v1/actors/" + created.resourceId());

        ActorResponse fetched = restTemplate.getForObject(
                "/api/v1/actors/" + created.resourceId(), ActorResponse.class);

        assertThat(fetched.state()).isEqualTo(ResourceState.ARCHIVED);
    }
}
```

- [ ] **Step 3: Write CanonicalIntegrationTest**

```java
// CanonicalIntegrationTest.java
package com.fos.governance.canonical;

import com.fos.governance.canonical.api.PlayerRequest;
import com.fos.governance.canonical.api.TeamRequest;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.canonical.TeamDTO;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CanonicalIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fos_governance");

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
    void should_create_player_and_return_201() {
        PlayerRequest request = new PlayerRequest(
                "Lionel Test", "CF", "AR", LocalDate.of(1987, 6, 24), null);

        ResponseEntity<PlayerDTO> response = restTemplate.postForEntity(
                "/api/v1/players", request, PlayerDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Lionel Test");
        assertThat(response.getBody().position()).isEqualTo("CF");
    }

    @Test
    void should_find_player_by_identity_for_dedup() {
        PlayerRequest request = new PlayerRequest(
                "Dedup Player", "CM", "BR", LocalDate.of(1995, 3, 15), null);

        // Create player
        restTemplate.postForObject("/api/v1/players", request, PlayerDTO.class);

        // Find by identity (dedup endpoint)
        ResponseEntity<PlayerDTO> found = restTemplate.getForEntity(
                "/api/v1/players/find?name=Dedup+Player&dob=1995-03-15&nationality=BR",
                PlayerDTO.class);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().name()).isEqualTo("Dedup Player");
    }

    @Test
    void should_not_find_nonexistent_player_by_identity() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/players/find?name=Ghost+Player&dob=2000-01-01&nationality=XX",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_create_team_and_return_201() {
        TeamRequest request = new TeamRequest("Test FC", "TFC", "ES", null);

        ResponseEntity<TeamDTO> response = restTemplate.postForEntity(
                "/api/v1/teams", request, TeamDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Test FC");
    }
}
```

- [ ] **Step 4: Run integration tests**

```bash
cd fos-governance-service
mvn test -Dtest="ActorIntegrationTest,CanonicalIntegrationTest" -q
```

Expected: BUILD SUCCESS — 7 tests pass (3 Actor + 4 Canonical)

- [ ] **Step 5: Commit**

```bash
git add fos-governance-service/src/test/
git commit -m "test(governance): add integration tests for Actor CRUD and Canonical Player/Team"
```

---

## Task 13: Global Exception Handler

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/config/GlobalExceptionHandler.java`

This ensures `EntityNotFoundException` returns 404 and validation errors return 400.

- [ ] **Step 1: Create GlobalExceptionHandler**

```java
// GlobalExceptionHandler.java
package com.fos.governance.config;

import com.fos.sdk.core.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(EntityNotFoundException ex) {
        return new ErrorResponse("NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new ErrorResponse("VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(IllegalStateException ex) {
        return new ErrorResponse("CONFLICT", ex.getMessage(), null);
    }
}
```

- [ ] **Step 2: Verify integration tests still pass with 404 handling**

```bash
cd fos-governance-service
mvn test -Dtest=CanonicalIntegrationTest#should_not_find_nonexistent_player_by_identity -q
```

Expected: PASS — `EntityNotFoundException` maps to HTTP 404

- [ ] **Step 3: Commit**

```bash
git add fos-governance-service/src/main/java/com/fos/governance/config/
git commit -m "feat(governance): add GlobalExceptionHandler for 404, 400, 409 responses"
```

---

## Task 14: Full Build Verification

- [ ] **Step 1: Build and install sdk-canonical**

```bash
cd fos-sdk
mvn install -pl sdk-canonical -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Build fos-governance-service**

```bash
cd fos-governance-service
mvn package -q
```

Expected: BUILD SUCCESS — JAR produced in `target/`

- [ ] **Step 3: Verify service starts with docker-compose infrastructure**

```bash
cd football-os-core
docker-compose up -d postgres kafka
# Wait 10 seconds for containers to be healthy
java -jar fos-governance-service/target/fos-governance-service-*.jar
```

Expected: Application starts on port 8081 without errors. Flyway migrations V001–V004 run and succeed. Log shows:
```
Flyway Community Edition ... has successfully applied 4 migrations to schema "public"
```

- [ ] **Step 4: Smoke test with curl**

```bash
# Create a player
curl -s -X POST http://localhost:8081/api/v1/players \
  -H "Content-Type: application/json" \
  -d '{"name":"Carlos Silva","position":"ST","nationality":"BR","dateOfBirth":"1995-06-15"}' | jq .

# Expected: {"id":"...","name":"Carlos Silva","position":"ST",...}

# Create an actor
curl -s -X POST http://localhost:8081/api/v1/actors \
  -H "Content-Type: application/json" \
  -d '{"email":"test@club.com","firstName":"Test","lastName":"User","role":"PLAYER"}' | jq .

# Expected: {"resourceId":"...","email":"test@club.com","role":"PLAYER","state":"DRAFT",...}
```

- [ ] **Step 5: Commit final state**

```bash
git add .
git commit -m "chore(governance): sprint 0.4 complete — identity + canonical + sdk-canonical"
```

---

## Sprint Test Criteria

Sprint 0.4 is complete when:

1. `mvn test` passes in `fos-sdk/sdk-canonical` (5+ tests: CanonicalServiceClient + CanonicalResolver)
2. `mvn test` passes in `fos-governance-service` (7+ integration tests: Actor + Canonical)
3. `POST /api/v1/actors` returns 201 with `state: DRAFT`
4. `GET /api/v1/actors/{id}` returns the actor
5. `DELETE /api/v1/actors/{id}` transitions actor to `state: ARCHIVED`
6. `POST /api/v1/players` returns 201
7. `GET /api/v1/players/find?name=...&dob=...&nationality=...` returns 200 when found, 404 when not
8. `POST /api/v1/teams` returns 201
9. Kafka shows FACT signals on `fos.identity.actor.created` and `fos.canonical.player.created` topics
10. Flyway migrations V001–V004 run cleanly on a fresh database

---

## What NOT to Include in This Sprint

- **Policy package** — Sprint 0.5 (OPA integration, `PolicyGuardAspect`)
- **Signal package** — Sprint 0.5 (Chain of Responsibility pipeline, NotificationPort)
- **fos_audit consumer** — Sprint 0.5
- **sdk-policy module** — Sprint 0.5
- **CanonicalResolver eviction from Kafka signals** — Sprint 0.6 (needs sdk-test infrastructure)
- **Match + TrainingSession canonical entities** — added when a domain service needs them (Phase 2)
- **Redis cache for CanonicalResolver** — Phase 1+ (replace ConcurrentHashMap when measured latency demands it)
- **Actor groups and team membership** — later sprint in Phase 0 or Phase 1
- **JWT protection on these endpoints** — Sprint 0.6 (gateway handles it; governance service itself does not validate JWTs in this sprint)

---

## SDK Dependencies Used

| Module | Usage |
|--------|-------|
| `sdk-core` | `ResourceState`, `ErrorResponse` |
| `sdk-events` | `FosKafkaProducer`, `KafkaTopics`, `SignalEnvelope`, `SignalType` |
| `sdk-security` | Deferred — JWT validation added in Sprint 0.6 |
| `sdk-canonical` | `CanonicalRef`, `CanonicalType`, `PlayerDTO`, `TeamDTO` (built in this sprint) |

---

## Kafka Topics Produced This Sprint

| Topic | Producer | Signal Type | When |
|-------|----------|-------------|------|
| `fos.identity.actor.created` | `ActorService` | FACT | After `POST /api/v1/actors` |
| `fos.identity.actor.deactivated` | `ActorService` | FACT | After `DELETE /api/v1/actors/{id}` |
| `fos.canonical.player.created` | `PlayerService` | FACT | After `POST /api/v1/players` |
| `fos.canonical.team.created` | `TeamService` | FACT | After `POST /api/v1/teams` |
