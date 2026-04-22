# Spring Profiles Configuration Plan — Football OS
## Full Agent Instruction Guide

> **For agentic workers:** Read this entire file before writing a single line.
> Every section is a direct instruction. Do not skip, reorder, or summarize tasks.
> Use checkbox (`- [ ]`) syntax to track every step.

---

## 0. What You Are Doing and Why

This project is called **Football OS**. It is a Maven multi-module monorepo
(`football-os-core`) containing four modules that have Spring Boot applications:

| Module | Port | Tech |
|---|---|---|
| `fos-gateway` | 8080 | Spring Cloud Gateway, Redis, Keycloak JWT |
| `fos-governance-service` | 8081 | PostgreSQL, Flyway, Kafka, OPA |
| `fos-workspace-service` | 8082 | MongoDB, Mongock, Kafka, MinIO, OnlyOffice |
| `fos-sdk` | library only | No Spring Boot app, no profiles needed |

> **`fos-gateway-service`** is a legacy/dead skeleton that is **NOT** part of the
> active Maven build. Do not touch it. Only `fos-gateway` is used.

**Your job in this task:** Create Spring profile configuration files for each of
the three active Spring Boot modules (`fos-gateway`, `fos-governance-service`,
`fos-workspace-service`). You will create **three profiles** per module:
`dev`, `staging`, and `prod`. You will also create one shared `logback-spring.xml`
per module.

**Why profiles?** A profile is a "mode switch". When running locally
(`--spring.profiles.active=dev`), the app uses relaxed settings, verbose
logging, and disabled security. In staging and production, real credentials come
from environment variables, security is enforced, and logging is minimal.

**The golden rule for `prod` profile files:** NEVER hardcode passwords, URLs,
or secrets. Always use `${ENV_VARIABLE}` syntax with NO default fallback.
If the env var is missing the app must FAIL to start. This is intentional.

---

## 1. Files You Will Create

```
football-os-core/
│
├── fos-gateway/
│   └── src/main/resources/
│       ├── application.yml                   REPLACE (full rewrite)
│       ├── application-dev.yml               CREATE
│       ├── application-staging.yml           CREATE
│       ├── application-prod.yml              CREATE
│       └── logback-spring.xml                CREATE
│
├── fos-governance-service/
│   └── src/main/resources/
│       ├── application.yml                   REPLACE (full rewrite)
│       ├── application-dev.yml               CREATE
│       ├── application-staging.yml           CREATE
│       ├── application-prod.yml              CREATE
│       └── logback-spring.xml                CREATE
│
└── fos-workspace-service/
    └── src/main/resources/
        ├── application.yml                   REPLACE (full rewrite)
        ├── application-dev.yml               CREATE
        ├── application-staging.yml           CREATE
        ├── application-prod.yml              CREATE
        └── logback-spring.xml                CREATE
```

**Total: 15 files.**

---

## 2. How Spring Profiles Work (Read Before Writing)

Spring loads configuration in this order:
1. `application.yml` — loaded always, for ALL profiles
2. `application-{profile}.yml` — loaded on top, OVERRIDES `application.yml`

So `application.yml` holds defaults and shared config. Profile files only
contain what is different from those defaults.

A profile is activated by:
- Setting env var: `SPRING_PROFILES_ACTIVE=dev`
- Maven flag: `-Dspring-boot.run.profiles=dev`
- IntelliJ VM options: `-Dspring.profiles.active=dev`

In `application.yml` we set:
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
```
This means: use the env var if set, otherwise default to `dev`.

---

## 3. pom.xml Dependency Addition (Do This First)

Before creating any YAML files, add the `logstash-logback-encoder` dependency
to each of the three service `pom.xml` files. This library is required for
JSON-formatted logs in staging and production.

### 3.1 — fos-gateway/pom.xml

- [ ] Open `fos-gateway/pom.xml`
- [ ] Add this dependency inside the `<dependencies>` block:

```xml
<!-- JSON structured logging for staging and production profiles -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

### 3.2 — fos-governance-service/pom.xml

- [ ] Open `fos-governance-service/pom.xml`
- [ ] Add the same dependency inside the `<dependencies>` block:

```xml
<!-- JSON structured logging for staging and production profiles -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

### 3.3 — fos-workspace-service/pom.xml

- [ ] Open `fos-workspace-service/pom.xml`
- [ ] Add the same dependency inside the `<dependencies>` block:

```xml
<!-- JSON structured logging for staging and production profiles -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```

---

## 4. MODULE: fos-gateway

### Context for the agent

`fos-gateway` is a **reactive** Spring Cloud Gateway. It uses:
- **WebFlux** (NOT the regular servlet stack — this is reactive)
- **Keycloak** at `http://localhost:8180/realms/fos` to validate JWTs via JWKS
- **Redis** at `localhost:6379` for rate limiting (100 req/min per actor)
- Two custom global filters: `CorrelationIdFilter` and `ActorIdEnrichmentFilter`
- A `SecurityConfig.java` that reads `${fos.security.enabled:true}` and skips
  JWT validation when it is `false` (dev only)
- Routes to governance on port 8081 and workspace on port 8082

### 4.1 — application.yml (REPLACE the existing file entirely)

- [ ] Write the following content to `fos-gateway/src/main/resources/application.yml`:

```yaml
# fos-gateway/src/main/resources/application.yml
# ─────────────────────────────────────────────────────────────────────────────
# SHARED BASE CONFIGURATION — applies to ALL profiles.
# Profile-specific files (application-dev.yml etc.) override individual keys.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  application:
    name: fos-gateway

  profiles:
    # Default to dev if no profile is set.
    # In production: export SPRING_PROFILES_ACTIVE=prod
    active: ${SPRING_PROFILES_ACTIVE:dev}

  security:
    oauth2:
      resourceserver:
        jwt:
          # The Keycloak JWKS endpoint. Gateway uses this to verify JWT signatures.
          # No secret key needed — Keycloak signs tokens with RSA; gateway only verifies.
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8180/realms/fos/protocol/openid-connect/certs}

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

  cloud:
    gateway:
      # Default rate limiter applied to EVERY route.
      # replenishRate: how many requests per second the bucket refills
      # burstCapacity: max burst before throttling kicks in
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter:
              replenishRate: 100
              burstCapacity: 120
            key-resolver: "#{@actorKeyResolver}"

server:
  port: ${SERVER_PORT:8080}
  compression:
    enabled: true
    min-response-size: 2048

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never

# FOS custom configuration
fos:
  security:
    # When false: JWT validation is completely skipped. ONLY for dev/tests.
    enabled: ${FOS_SECURITY_ENABLED:true}
  governance:
    url: ${GOVERNANCE_URL:http://localhost:8081}
  workspace:
    url: ${WORKSPACE_URL:http://localhost:8082}
```

### 4.2 — application-dev.yml

- [ ] Create `fos-gateway/src/main/resources/application-dev.yml` with this content:

```yaml
# fos-gateway/src/main/resources/application-dev.yml
# ─────────────────────────────────────────────────────────────────────────────
# DEV PROFILE — for local development on your machine.
#
# Key differences from base:
#   - Security disabled: no JWT required, all requests pass through
#   - Rate limiter set to very high values so it never blocks you during dev
#   - All actuator endpoints exposed so you can inspect beans, env, health
#   - Full debug logging
# ─────────────────────────────────────────────────────────────────────────────

spring:
  data:
    redis:
      host: localhost
      port: 6379

  security:
    oauth2:
      resourceserver:
        jwt:
          # Points to local Keycloak Docker container.
          # Not actually used when fos.security.enabled=false,
          # but Spring needs a value to start without errors.
          jwk-set-uri: http://localhost:8180/realms/fos/protocol/openid-connect/certs

fos:
  security:
    # CRITICAL: false = no JWT validation. ONLY safe in dev/tests.
    enabled: false
  governance:
    url: http://localhost:8081
  workspace:
    url: http://localhost:8082

spring:
  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter:
              # Very high in dev so rate limiting never blocks you
              replenishRate: 10000
              burstCapacity: 10000
            key-resolver: "#{@actorKeyResolver}"

management:
  endpoints:
    web:
      exposure:
        # Expose everything in dev: beans, env, mappings, conditions, etc.
        include: "*"
  endpoint:
    health:
      show-details: always

server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: always
    include-exception: true

logging:
  level:
    root: INFO
    # Gateway package — show every filter decision, routing decision
    com.fos.gateway: DEBUG
    # Spring Cloud Gateway routing engine
    org.springframework.cloud.gateway: DEBUG
    # Spring Security — show JWT validation steps, access decisions
    org.springframework.security: DEBUG
    # WebFlux — show reactive pipeline steps
    org.springframework.web.reactive: DEBUG
    # Redis — show rate limiter interactions
    org.springframework.data.redis: DEBUG
```

### 4.3 — application-staging.yml

- [ ] Create `fos-gateway/src/main/resources/application-staging.yml` with this content:

```yaml
# fos-gateway/src/main/resources/application-staging.yml
# ─────────────────────────────────────────────────────────────────────────────
# STAGING PROFILE — production-like server used for QA testing.
#
# Key differences from dev:
#   - Security fully enabled (real JWTs required)
#   - Credentials from environment variables
#   - Reduced logging (no DEBUG)
#   - Stack traces NOT sent in HTTP error responses
# ─────────────────────────────────────────────────────────────────────────────

spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}

fos:
  security:
    enabled: true
  governance:
    url: ${GOVERNANCE_URL}
  workspace:
    url: ${WORKSPACE_URL}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: never
    include-exception: false

logging:
  level:
    root: WARN
    com.fos.gateway: INFO
    org.springframework.cloud.gateway: WARN
    org.springframework.security: WARN
    org.springframework.web.reactive: WARN
    org.springframework.data.redis: WARN
```

### 4.4 — application-prod.yml

- [ ] Create `fos-gateway/src/main/resources/application-prod.yml` with this content:

```yaml
# fos-gateway/src/main/resources/application-prod.yml
# ─────────────────────────────────────────────────────────────────────────────
# PRODUCTION PROFILE
#
# RULES:
#   - NO default values (no ":fallback" syntax) on any sensitive property.
#     If an env var is missing, the app MUST fail to start. This is correct.
#   - NO stack traces ever sent to API callers.
#   - Minimal logging (errors only).
# ─────────────────────────────────────────────────────────────────────────────

spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}

  cloud:
    gateway:
      default-filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter:
              replenishRate: 100
              burstCapacity: 120
            key-resolver: "#{@actorKeyResolver}"

fos:
  security:
    enabled: true
  governance:
    url: ${GOVERNANCE_URL}
  workspace:
    url: ${WORKSPACE_URL}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never

server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false

logging:
  level:
    root: ERROR
    com.fos.gateway: WARN
    org.springframework.cloud.gateway: ERROR
    org.springframework.security: ERROR
    org.springframework.data.redis: ERROR
```

### 4.5 — logback-spring.xml

- [ ] Create `fos-gateway/src/main/resources/logback-spring.xml` with this content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  fos-gateway/src/main/resources/logback-spring.xml
  Controls how log messages are formatted.
  Uses <springProfile> tags so the format changes per active profile.
-->
<configuration>

  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

  <!-- ════════════════════════════════════════════════════════════════════
       DEV: Human-readable colorized output to console
       ════════════════════════════════════════════════════════════════════ -->
  <springProfile name="dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <!--
          Pattern explanation:
            %d{HH:mm:ss.SSS}     = timestamp
            %highlight(%-5level)  = color-coded log level
            [%15.15t]             = thread name (max 15 chars)
            %-40.40logger{39}     = logger/class name (max 40 chars)
            %msg%n                = the log message + newline
            %ex                   = stack trace if exception present
        -->
        <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%15.15t] %cyan(%-40.40logger{39}) : %msg%n%ex</pattern>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

  <!-- ════════════════════════════════════════════════════════════════════
       STAGING: JSON to console + rolling file
       ════════════════════════════════════════════════════════════════════ -->
  <springProfile name="staging">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-gateway","env":"staging"}</customFields>
      </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/fos/gateway/gateway.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/var/log/fos/gateway/gateway.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>3GB</totalSizeCap>
      </rollingPolicy>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-gateway","env":"staging"}</customFields>
      </encoder>
    </appender>
    <root level="WARN">
      <appender-ref ref="JSON_CONSOLE"/>
      <appender-ref ref="FILE"/>
    </root>
  </springProfile>

  <!-- ════════════════════════════════════════════════════════════════════
       PROD: JSON to console + rolling file, 90 day retention
       ════════════════════════════════════════════════════════════════════ -->
  <springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-gateway","env":"prod"}</customFields>
      </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/fos/gateway/gateway.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/var/log/fos/gateway/gateway.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>90</maxHistory>
        <totalSizeCap>10GB</totalSizeCap>
      </rollingPolicy>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-gateway","env":"prod"}</customFields>
      </encoder>
    </appender>
    <root level="ERROR">
      <appender-ref ref="JSON_CONSOLE"/>
      <appender-ref ref="FILE"/>
    </root>
  </springProfile>

</configuration>
```

---

## 5. MODULE: fos-governance-service

### Context for the agent

`fos-governance-service` is a standard **servlet-stack** Spring Boot application
(NOT reactive). It uses:
- **PostgreSQL 16** at `localhost:5432`, database `fos_governance`
- **Flyway** for SQL migrations (V001 through V005 scripts exist under
  `src/main/resources/db/migration/`)
- **Kafka** for emitting FACT and AUDIT signals
- **OPA** (Open Policy Agent) sidecar at `http://localhost:8181`
- **Keycloak** for JWT validation
- **Spring Data JPA** with Hibernate
- Five PostgreSQL schemas: `fos_identity`, `fos_canonical`, `fos_policy`,
  `fos_signal`, `fos_audit`
- Flyway history tracked in `public.flyway_schema_history`
- JPA default schema: `fos_identity`

### 5.1 — application.yml (REPLACE the existing file entirely)

- [ ] Write the following content to
  `fos-governance-service/src/main/resources/application.yml`:

```yaml
# fos-governance-service/src/main/resources/application.yml
# ─────────────────────────────────────────────────────────────────────────────
# SHARED BASE CONFIGURATION — applies to ALL profiles.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  application:
    name: fos-governance-service

  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  # ── PostgreSQL datasource ──────────────────────────────────────────────────
  datasource:
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/fos_governance}
    username: ${POSTGRES_USER:fos}
    password: ${POSTGRES_PASSWORD:fos}
    driver-class-name: org.postgresql.Driver
    # HikariCP connection pool settings
    hikari:
      # Max connections in the pool
      maximum-pool-size: 10
      # Min connections kept alive
      minimum-idle: 2
      # How long a connection can be idle before being removed (ms)
      idle-timeout: 300000
      # How long to wait for a connection from the pool (ms)
      connection-timeout: 30000
      pool-name: FosGovernancePool

  # ── JPA / Hibernate ────────────────────────────────────────────────────────
  jpa:
    # validate = Hibernate checks DB schema matches entity classes, but never changes DB
    # NEVER use create, create-drop, or update in any environment.
    # Flyway owns the schema — Hibernate only reads it.
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        # Governance entities live in multiple schemas.
        # Each @Entity uses @Table(schema="fos_identity") etc.
        # This default applies when no schema is specified on @Table.
        default_schema: fos_identity
        # Show SQL in logs — overridden per profile
        show_sql: false
        format_sql: true
        # Dialect
        dialect: org.hibernate.dialect.PostgreSQLDialect
    # Disable open-session-in-view (causes lazy loading issues — always disable)
    open-in-view: false

  # ── Flyway ─────────────────────────────────────────────────────────────────
  flyway:
    enabled: true
    # Where Flyway looks for migration scripts
    locations: classpath:db/migration
    # Flyway stores its own history table in the public schema.
    # This is separate from the fos_identity, fos_canonical etc. schemas.
    default-schema: public
    # If true: Flyway fails to start if any migration file has been modified
    # after it was applied. Keep true — prevents accidental migration tampering.
    validate-on-migrate: true
    # Baseline: if existing tables are found with no Flyway history, treat them
    # as baseline version 0 and continue. Set to false after first deployment.
    baseline-on-migrate: false

  # ── Kafka ──────────────────────────────────────────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      retries: 3
      properties:
        linger.ms: 100
    consumer:
      group-id: fos-governance
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false

  # ── Security (Keycloak JWT) ────────────────────────────────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8180/realms/fos/protocol/openid-connect/certs}

  # ── JSON ──────────────────────────────────────────────────────────────────
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
    default-property-inclusion: non_null

server:
  port: ${SERVER_PORT:8081}
  compression:
    enabled: true
    min-response-size: 2048
  error:
    include-message: always
    include-binding-errors: always

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never

# FOS custom settings
fos:
  security:
    enabled: ${FOS_SECURITY_ENABLED:true}
  opa:
    # OPA sidecar runs alongside this service
    url: ${OPA_URL:http://localhost:8181}
  canonical:
    service-url: ${CANONICAL_SERVICE_URL:http://localhost:8081}
```

### 5.2 — application-dev.yml

- [ ] Create `fos-governance-service/src/main/resources/application-dev.yml`:

```yaml
# fos-governance-service/src/main/resources/application-dev.yml
# ─────────────────────────────────────────────────────────────────────────────
# DEV PROFILE — local development.
#
# Key differences:
#   - Hardcoded local credentials (Docker container defaults)
#   - Security disabled
#   - SQL shown in logs for debugging queries
#   - Flyway baseline-on-migrate=true (safe for first-time local setup)
#   - All actuator endpoints exposed
#   - DEBUG logging for everything FOS-related
# ─────────────────────────────────────────────────────────────────────────────

spring:
  datasource:
    # Local Docker PostgreSQL — default credentials from docker-compose.yml
    url: jdbc:postgresql://localhost:5432/fos_governance
    username: fos
    password: fos

  jpa:
    properties:
      hibernate:
        # Show every SQL query in the console — very useful for debugging
        show_sql: true
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    default-schema: public
    validate-on-migrate: true
    # true = if DB has tables but no Flyway history, treat current state as v0
    # This is safe for first-time local setup where you have an existing DB
    baseline-on-migrate: true
    baseline-version: 0

  kafka:
    bootstrap-servers: localhost:9092

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8180/realms/fos/protocol/openid-connect/certs

fos:
  security:
    enabled: false
  opa:
    url: http://localhost:8181

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always

server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: always
    include-exception: true

logging:
  level:
    root: INFO
    # All governance code — show DEBUG
    com.fos.governance: DEBUG
    # All SDK code used by governance
    com.fos.sdk: DEBUG
    # Spring Web MVC — show every HTTP request and response
    org.springframework.web: DEBUG
    # Spring Security — show auth chain decisions
    org.springframework.security: DEBUG
    # Hibernate SQL — already shown via show_sql=true but this adds parameter values
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE
    # Spring Data JPA queries
    org.springframework.data.jpa: DEBUG
    # Flyway — show each migration script as it runs
    org.flywaydb: DEBUG
    # Kafka producer/consumer activity
    org.springframework.kafka: DEBUG
    org.apache.kafka: INFO
    # OPA HTTP calls
    org.springframework.web.client: DEBUG
```

### 5.3 — application-staging.yml

- [ ] Create `fos-governance-service/src/main/resources/application-staging.yml`:

```yaml
# fos-governance-service/src/main/resources/application-staging.yml
# ─────────────────────────────────────────────────────────────────────────────
# STAGING PROFILE — production-like QA environment.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  datasource:
    url: ${POSTGRES_URL}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    hikari:
      maximum-pool-size: 15
      minimum-idle: 3

  jpa:
    properties:
      hibernate:
        show_sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    default-schema: public
    validate-on-migrate: true
    baseline-on-migrate: false

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}

fos:
  security:
    enabled: true
  opa:
    url: ${OPA_URL}
  canonical:
    service-url: ${CANONICAL_SERVICE_URL}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: never
    include-exception: false

logging:
  level:
    root: WARN
    com.fos.governance: INFO
    com.fos.sdk: INFO
    org.springframework.web: WARN
    org.springframework.security: WARN
    org.hibernate.SQL: WARN
    org.flywaydb: INFO
    org.springframework.kafka: WARN
    org.springframework.web.client: WARN
```

### 5.4 — application-prod.yml

- [ ] Create `fos-governance-service/src/main/resources/application-prod.yml`:

```yaml
# fos-governance-service/src/main/resources/application-prod.yml
# ─────────────────────────────────────────────────────────────────────────────
# PRODUCTION PROFILE
#
# SECURITY RULES:
#   - NO defaults (no :fallback) for any sensitive value.
#     Missing env vars = app fails to start. This is CORRECT behavior.
#   - NO stack traces in responses.
#   - NO SQL in logs.
#   - Minimal logging — errors only.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  datasource:
    url: ${POSTGRES_URL}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 600000
      connection-timeout: 30000

  jpa:
    properties:
      hibernate:
        show_sql: false
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    default-schema: public
    validate-on-migrate: true
    baseline-on-migrate: false

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      # In production: require ALL replicas to acknowledge before message is committed
      acks: all
      retries: 5

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}

fos:
  security:
    enabled: true
  opa:
    url: ${OPA_URL}
  canonical:
    service-url: ${CANONICAL_SERVICE_URL}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never

server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false

logging:
  level:
    root: ERROR
    com.fos.governance: WARN
    com.fos.sdk: WARN
    org.springframework: ERROR
    org.hibernate: ERROR
    org.flywaydb: WARN
    org.springframework.kafka: ERROR
    org.apache.kafka: ERROR
```

### 5.5 — logback-spring.xml

- [ ] Create `fos-governance-service/src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  fos-governance-service/src/main/resources/logback-spring.xml
-->
<configuration>

  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

  <!-- DEV: colorized human-readable console -->
  <springProfile name="dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%15.15t] %cyan(%-40.40logger{39}) : %msg%n%ex</pattern>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

  <!-- STAGING: JSON console + rolling file, 30 day retention -->
  <springProfile name="staging">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-governance","env":"staging"}</customFields>
      </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/fos/governance/governance.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/var/log/fos/governance/governance.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>3GB</totalSizeCap>
      </rollingPolicy>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-governance","env":"staging"}</customFields>
      </encoder>
    </appender>
    <root level="WARN">
      <appender-ref ref="JSON_CONSOLE"/>
      <appender-ref ref="FILE"/>
    </root>
  </springProfile>

  <!-- PROD: JSON console + rolling file, 90 day retention -->
  <springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-governance","env":"prod"}</customFields>
      </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/fos/governance/governance.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/var/log/fos/governance/governance.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>90</maxHistory>
        <totalSizeCap>10GB</totalSizeCap>
      </rollingPolicy>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-governance","env":"prod"}</customFields>
      </encoder>
    </appender>
    <root level="ERROR">
      <appender-ref ref="JSON_CONSOLE"/>
      <appender-ref ref="FILE"/>
    </root>
  </springProfile>

</configuration>
```

---

## 6. MODULE: fos-workspace-service

### Context for the agent

`fos-workspace-service` is a standard servlet-stack Spring Boot application.
It uses:
- **MongoDB 7** at `localhost:27017`, database `fos_workspace`
- **Mongock** for MongoDB index/collection migrations (NOT Flyway)
  - Mongock scan package: `com.fos.workspace.db.migration`
  - Migrations are `@ChangeUnit` annotated classes, NOT SQL files
- **Kafka** for emitting FACT, AUDIT, ALERT signals
- **MinIO** for file storage via `StoragePort` abstraction
  - `fos.storage.provider=noop` in dev (no MinIO container needed)
  - `fos.storage.provider=minio` uses local Docker MinIO
  - `fos.storage.provider=s3` or `azure` in production
- **OnlyOffice** at `http://localhost:8090` for document editing
- **Keycloak** for JWT validation
- **OPA** via `PolicyClient` (calls governance, NOT OPA directly)
- `fos.security.enabled=false` in dev disables JWT validation

### 6.1 — application.yml (REPLACE the existing file entirely)

- [ ] Write the following content to
  `fos-workspace-service/src/main/resources/application.yml`:

```yaml
# fos-workspace-service/src/main/resources/application.yml
# ─────────────────────────────────────────────────────────────────────────────
# SHARED BASE CONFIGURATION — applies to ALL profiles.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  application:
    name: fos-workspace-service

  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

  # ── MongoDB ────────────────────────────────────────────────────────────────
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/fos_workspace}
      # NEVER true — indexes are managed exclusively by Mongock migration classes.
      # Setting this to true would create duplicate indexes and race conditions.
      auto-index-creation: false

  # ── Kafka ──────────────────────────────────────────────────────────────────
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      retries: 3
      properties:
        linger.ms: 100
    consumer:
      group-id: fos-workspace
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false

  # ── Security ──────────────────────────────────────────────────────────────
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI:http://localhost:8180/realms/fos/protocol/openid-connect/certs}

  # ── JSON ──────────────────────────────────────────────────────────────────
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
    default-property-inclusion: non_null

  # ── SQL init ──────────────────────────────────────────────────────────────
  # Workspace uses MongoDB so there is no SQL data.sql to load.
  # This key is present for completeness and set to 'never' in all profiles.
  sql:
    init:
      mode: never

server:
  port: ${SERVER_PORT:8082}
  compression:
    enabled: true
    min-response-size: 2048
  error:
    include-message: always
    include-binding-errors: always

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: never

# ── Mongock ────────────────────────────────────────────────────────────────
# Mongock is the MongoDB equivalent of Flyway.
# It scans the migration package for @ChangeUnit classes and runs only
# the ones that have not been executed yet (tracked in mongockChangeLog collection).
mongock:
  migration-scan-package: com.fos.workspace.db.migration
  enabled: true

# ── FOS SDK client configuration ───────────────────────────────────────────
fos:
  security:
    enabled: ${FOS_SECURITY_ENABLED:true}
  policy:
    # PolicyClient sends HTTP POST to this URL to evaluate OPA decisions.
    # This points to fos-governance-service — workspace NEVER calls OPA directly.
    service-url: ${GOVERNANCE_URL:http://localhost:8081}
  canonical:
    # CanonicalResolver fetches player/team identity from governance canonical API.
    service-url: ${GOVERNANCE_URL:http://localhost:8081}
  storage:
    # Controls which StoragePort adapter is active.
    # noop    = NoopStorageAdapter (returns fake URLs, no MinIO needed) — use in dev/tests
    # minio   = MinioStorageAdapter (real MinIO container or server)
    # s3      = S3StorageAdapter (AWS S3)
    # azure   = AzureBlobStorageAdapter
    provider: ${STORAGE_PROVIDER:noop}
  onlyoffice:
    document-server-url: ${ONLYOFFICE_URL:http://localhost:8090}
    # Secret shared between this service and OnlyOffice Document Server.
    # Used to sign JWT configs so OnlyOffice accepts them.
    jwt-secret: ${ONLYOFFICE_JWT_SECRET:change-this-in-production}
    token-expiry-minutes: 60

# ── MinIO connection (only used when fos.storage.provider=minio) ───────────
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ${MINIO_BUCKET:fos-workspace}
```

### 6.2 — application-dev.yml

- [ ] Create `fos-workspace-service/src/main/resources/application-dev.yml`:

```yaml
# fos-workspace-service/src/main/resources/application-dev.yml
# ─────────────────────────────────────────────────────────────────────────────
# DEV PROFILE — local development.
#
# Key differences:
#   - Security disabled (no JWT required)
#   - NoopStorageAdapter (no MinIO container needed)
#   - Local MongoDB Docker container
#   - All actuator endpoints exposed
#   - Full DEBUG logging
#   - Mongock logs every migration step
# ─────────────────────────────────────────────────────────────────────────────

spring:
  data:
    mongodb:
      # Local Docker MongoDB — started by docker-compose up -d
      uri: mongodb://localhost:27017/fos_workspace_dev

  kafka:
    bootstrap-servers: localhost:9092

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8180/realms/fos/protocol/openid-connect/certs

fos:
  security:
    # false = no JWT validation. Your dev API calls work without Keycloak tokens.
    enabled: false
  storage:
    # noop = no real MinIO needed. Upload/download return fake URLs.
    # Change to 'minio' when you want to test real file uploads locally.
    provider: noop
  policy:
    service-url: http://localhost:8081
  canonical:
    service-url: http://localhost:8081
  onlyoffice:
    document-server-url: http://localhost:8090
    # Simple known secret for local dev — easy to decode manually if needed
    jwt-secret: dev-onlyoffice-secret-key-32-chars!!

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: fos-workspace-dev

mongock:
  migration-scan-package: com.fos.workspace.db.migration
  enabled: true

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always

server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: always
    include-exception: true

logging:
  level:
    root: INFO
    # All workspace business code — DEBUG so you see every service call
    com.fos.workspace: DEBUG
    # All SDK code used by workspace (PolicyClient, FosKafkaProducer, etc.)
    com.fos.sdk: DEBUG
    # Spring Web — see every incoming HTTP request
    org.springframework.web: DEBUG
    # Spring Security — see auth decisions (useful even with security disabled)
    org.springframework.security: DEBUG
    # Spring Data MongoDB — see every query
    org.springframework.data.mongodb: DEBUG
    org.mongodb.driver: INFO
    # Spring Kafka — see producer/consumer activity
    org.springframework.kafka: DEBUG
    org.apache.kafka: INFO
    # Mongock — see which migrations run and which are skipped
    io.mongock: DEBUG
    # HTTP client calls to governance service (PolicyClient, CanonicalServiceClient)
    org.springframework.web.client: DEBUG
```

### 6.3 — application-staging.yml

- [ ] Create `fos-workspace-service/src/main/resources/application-staging.yml`:

```yaml
# fos-workspace-service/src/main/resources/application-staging.yml
# ─────────────────────────────────────────────────────────────────────────────
# STAGING PROFILE — production-like QA environment.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}

fos:
  security:
    enabled: true
  storage:
    provider: ${STORAGE_PROVIDER:minio}
  policy:
    service-url: ${GOVERNANCE_URL}
  canonical:
    service-url: ${GOVERNANCE_URL}
  onlyoffice:
    document-server-url: ${ONLYOFFICE_URL}
    jwt-secret: ${ONLYOFFICE_JWT_SECRET}

minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  # Staging uses a separate bucket so staging uploads never mix with prod data
  bucket: fos-workspace-staging

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

server:
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: never
    include-exception: false

logging:
  level:
    root: WARN
    com.fos.workspace: INFO
    com.fos.sdk: INFO
    org.springframework.web: WARN
    org.springframework.security: WARN
    org.springframework.data.mongodb: WARN
    org.mongodb.driver: WARN
    org.springframework.kafka: WARN
    io.mongock: INFO
    org.springframework.web.client: WARN
```

### 6.4 — application-prod.yml

- [ ] Create `fos-workspace-service/src/main/resources/application-prod.yml`:

```yaml
# fos-workspace-service/src/main/resources/application-prod.yml
# ─────────────────────────────────────────────────────────────────────────────
# PRODUCTION PROFILE
#
# RULES:
#   - NO defaults on any sensitive property.
#   - Missing env var = app fails to start. CORRECT.
#   - NO stack traces in responses.
#   - NO debug/info logs from business code.
# ─────────────────────────────────────────────────────────────────────────────

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI}

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    producer:
      acks: all
      retries: 5

  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${KEYCLOAK_JWKS_URI}

fos:
  security:
    enabled: true
  storage:
    provider: ${STORAGE_PROVIDER}
  policy:
    service-url: ${GOVERNANCE_URL}
  canonical:
    service-url: ${GOVERNANCE_URL}
  onlyoffice:
    document-server-url: ${ONLYOFFICE_URL}
    jwt-secret: ${ONLYOFFICE_JWT_SECRET}

minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET}

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      show-details: never

server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false

logging:
  level:
    root: ERROR
    com.fos.workspace: WARN
    com.fos.sdk: WARN
    org.springframework: ERROR
    org.springframework.data.mongodb: ERROR
    org.mongodb.driver: ERROR
    org.springframework.kafka: ERROR
    io.mongock: WARN
```

### 6.5 — logback-spring.xml

- [ ] Create `fos-workspace-service/src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!--
  fos-workspace-service/src/main/resources/logback-spring.xml
-->
<configuration>

  <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

  <!-- DEV: colorized human-readable console -->
  <springProfile name="dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%15.15t] %cyan(%-40.40logger{39}) : %msg%n%ex</pattern>
      </encoder>
    </appender>
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>

  <!-- STAGING: JSON console + rolling file, 30 day retention -->
  <springProfile name="staging">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-workspace","env":"staging"}</customFields>
      </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/fos/workspace/workspace.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/var/log/fos/workspace/workspace.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
        <totalSizeCap>3GB</totalSizeCap>
      </rollingPolicy>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-workspace","env":"staging"}</customFields>
      </encoder>
    </appender>
    <root level="WARN">
      <appender-ref ref="JSON_CONSOLE"/>
      <appender-ref ref="FILE"/>
    </root>
  </springProfile>

  <!-- PROD: JSON console + rolling file, 90 day retention -->
  <springProfile name="prod">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-workspace","env":"prod"}</customFields>
      </encoder>
    </appender>
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>/var/log/fos/workspace/workspace.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>/var/log/fos/workspace/workspace.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>90</maxHistory>
        <totalSizeCap>10GB</totalSizeCap>
      </rollingPolicy>
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <customFields>{"service":"fos-workspace","env":"prod"}</customFields>
      </encoder>
    </appender>
    <root level="ERROR">
      <appender-ref ref="JSON_CONSOLE"/>
      <appender-ref ref="FILE"/>
    </root>
  </springProfile>

</configuration>
```

---

## 7. Verification Checklist

After creating all 15 files, run these commands to verify nothing is broken.

### 7.1 — Build all modules

- [ ] Run from the monorepo root:
```bash
cd football-os-core
mvn clean install -DskipTests -q
```
Expected: `BUILD SUCCESS`

### 7.2 — Start with dev profile and check health

- [ ] Start infrastructure first:
```bash
cd football-os-core
docker-compose up -d
```

- [ ] Start fos-governance-service:
```bash
cd fos-governance-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Expected: Starts on port 8081. Flyway runs V001-V005 migrations. No errors.

- [ ] In a new terminal, start fos-workspace-service:
```bash
cd fos-workspace-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Expected: Starts on port 8082. Mongock runs Migration001-Migration004. No errors.

- [ ] In a new terminal, start fos-gateway:
```bash
cd fos-gateway
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
Expected: Starts on port 8080. No errors.

- [ ] Verify all three health endpoints:
```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```
Expected for all three: `{"status":"UP"}`

### 7.3 — Verify profile logging (dev shows colored text)

- [ ] Check that fos-workspace-service console shows colorized text logs, NOT JSON.
  If you see JSON in dev, the `logback-spring.xml` springProfile tags are not matching.

### 7.4 — Verify security is disabled in dev

- [ ] Call a protected workspace endpoint WITHOUT any Authorization header:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health
```
Expected: `200` (security disabled in dev)

- [ ] Call the same endpoint through the gateway:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health
```
Expected: `200` (health endpoint is always public, even in prod)

---

## 8. Environment Variables Reference

This is the complete list of environment variables each service reads.
Use this when configuring CI/CD, Docker, or Kubernetes for staging/prod.

### fos-gateway environment variables

| Variable | Required in | Default in dev | Example prod value |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | `dev` | `prod` |
| `KEYCLOAK_JWKS_URI` | staging, prod | local Keycloak URL | `https://auth.fos.com/realms/fos/protocol/openid-connect/certs` |
| `REDIS_HOST` | staging, prod | `localhost` | `redis.internal` |
| `REDIS_PORT` | staging, prod | `6379` | `6379` |
| `FOS_SECURITY_ENABLED` | all | `true` (override to `false` in dev) | `true` |
| `GOVERNANCE_URL` | all | `http://localhost:8081` | `http://governance.internal:8081` |
| `WORKSPACE_URL` | all | `http://localhost:8082` | `http://workspace.internal:8082` |
| `SERVER_PORT` | optional | `8080` | `8080` |

### fos-governance-service environment variables

| Variable | Required in | Default in dev | Example prod value |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | `dev` | `prod` |
| `POSTGRES_URL` | staging, prod | local JDBC URL | `jdbc:postgresql://db.internal:5432/fos_governance` |
| `POSTGRES_USER` | staging, prod | `fos` | `fos_prod_user` |
| `POSTGRES_PASSWORD` | staging, prod | `fos` | `<secret>` |
| `KAFKA_BOOTSTRAP_SERVERS` | staging, prod | `localhost:9092` | `kafka.internal:9092` |
| `KEYCLOAK_JWKS_URI` | staging, prod | local Keycloak URL | see above |
| `OPA_URL` | staging, prod | `http://localhost:8181` | `http://opa.internal:8181` |
| `CANONICAL_SERVICE_URL` | staging, prod | `http://localhost:8081` | `http://governance.internal:8081` |
| `FOS_SECURITY_ENABLED` | all | `true` | `true` |
| `SERVER_PORT` | optional | `8081` | `8081` |

### fos-workspace-service environment variables

| Variable | Required in | Default in dev | Example prod value |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | `dev` | `prod` |
| `MONGODB_URI` | staging, prod | local MongoDB URI | `mongodb://user:pass@mongo.internal:27017/fos_workspace` |
| `KAFKA_BOOTSTRAP_SERVERS` | staging, prod | `localhost:9092` | `kafka.internal:9092` |
| `KEYCLOAK_JWKS_URI` | staging, prod | local Keycloak URL | see above |
| `GOVERNANCE_URL` | staging, prod | `http://localhost:8081` | `http://governance.internal:8081` |
| `STORAGE_PROVIDER` | all | `noop` | `s3` or `azure` |
| `MINIO_ENDPOINT` | when minio | `http://localhost:9000` | `http://minio.internal:9000` |
| `MINIO_ACCESS_KEY` | when minio | `minioadmin` | `<secret>` |
| `MINIO_SECRET_KEY` | when minio | `minioadmin` | `<secret>` |
| `MINIO_BUCKET` | when minio | `fos-workspace` | `fos-workspace-prod` |
| `ONLYOFFICE_URL` | all | `http://localhost:8090` | `https://onlyoffice.internal` |
| `ONLYOFFICE_JWT_SECRET` | all | simple dev string | `<secret>` |
| `FOS_SECURITY_ENABLED` | all | `true` | `true` |
| `SERVER_PORT` | optional | `8082` | `8082` |

---

## 9. Important Notes for the Agent

1. **Do NOT touch `fos-gateway-service/`** — this is a dead legacy module.
   Only `fos-gateway/` is used.

2. **Do NOT touch `fos-sdk/`** — it is a library with no Spring Boot application.
   It has no `application.yml` and needs no profiles.

3. **The `fos-governance-service` uses Flyway for PostgreSQL migrations.**
   Do not add `spring.jpa.hibernate.ddl-auto=create` or `update` anywhere.
   Only `validate` is allowed.

4. **The `fos-workspace-service` uses Mongock for MongoDB migrations.**
   Do not add `spring.data.mongodb.auto-index-creation=true` anywhere.
   Only `false` is allowed.

5. **In `application-dev.yml` for workspace**, the MongoDB database name is
   `fos_workspace_dev` (not `fos_workspace`) so dev data stays separate.

6. **In `application-staging.yml` for workspace**, the MinIO bucket is
   `fos-workspace-staging` (not the prod bucket). This is intentional.

7. **The `logback-spring.xml` files use `<springProfile name="dev">` tags.**
   These tags are Spring Boot specific and only work inside `logback-spring.xml`
   (not `logback.xml`). The filename MUST be `logback-spring.xml`.

8. **Profile activation default is always `dev`** via
   `${SPRING_PROFILES_ACTIVE:dev}`. This means a developer who clones the repo
   and runs `mvn spring-boot:run` without any configuration gets the dev profile
   automatically.
