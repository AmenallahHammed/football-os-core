# Phase 0 Sprint 0.1 — Dev Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docker-compose up` brings up the full infra stack; Maven multi-module build succeeds; every service skeleton has `/actuator/health` returning 200.

**Architecture:** `football-os-core` Maven multi-module monorepo. SDK sub-modules and one governance service (`fos-governance-service`) plus `fos-gateway` live here. Services are Spring Boot 3.3.x skeleton stubs only this sprint — no business logic yet.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Maven 3.9.x, Docker Compose v2, GitHub Actions, Keycloak 24, PostgreSQL 16, MongoDB 7, Apache Kafka (with Zookeeper), MinIO, OpenSearch, Redis

---

## File Map

```
football-os-core/
├── pom.xml                                     CREATE — parent BOM
├── docker-compose.yml                          CREATE — full local stack (includes Redis)
├── .github/
│   └── workflows/
│       └── ci.yml                              CREATE — CI pipeline
├── fos-sdk/
│   └── pom.xml                                 CREATE — SDK aggregator (7 modules)
├── fos-governance-service/
│   ├── pom.xml                                 CREATE — single governance app
│   └── src/main/
│       ├── java/com/fos/governance/GovernanceApp.java
│       └── resources/application.yml
└── fos-gateway/
    ├── pom.xml                                 CREATE — gateway module
    └── src/main/
        ├── java/com/fos/gateway/GatewayApp.java
        └── resources/application.yml
```

---

## Task 1: Parent POM + Maven Multi-Module Scaffold

**Files:**
- Create: `football-os-core/pom.xml`
- Create: `football-os-core/fos-sdk/pom.xml`

- [ ] **Step 1: Create the parent POM**

```xml
<!-- football-os-core/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.fos</groupId>
  <artifactId>football-os-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Football OS Core</name>

  <modules>
    <module>fos-sdk</module>
    <module>fos-governance-service</module>
    <module>fos-gateway</module>
  </modules>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <properties>
    <java.version>21</java.version>
    <fos.sdk.version>0.1.0-SNAPSHOT</fos.sdk.version>
    <testcontainers.version>1.20.1</testcontainers.version>
    <mapstruct.version>1.5.5.Final</mapstruct.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- SDK modules -->
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
      <dependency>
        <groupId>com.fos</groupId>
        <artifactId>sdk-policy</artifactId>
        <version>${fos.sdk.version}</version>
      </dependency>
      <dependency>
        <groupId>com.fos</groupId>
        <artifactId>sdk-canonical</artifactId>
        <version>${fos.sdk.version}</version>
      </dependency>
      <dependency>
        <groupId>com.fos</groupId>
        <artifactId>sdk-test</artifactId>
        <version>${fos.sdk.version}</version>
        <scope>test</scope>
      </dependency>
      <!-- Testcontainers BOM -->
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
          <groupId>org.flywaydb</groupId>
          <artifactId>flyway-maven-plugin</artifactId>
          <version>10.10.0</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

- [ ] **Step 2: Create the SDK aggregator POM**

```xml
<!-- football-os-core/fos-sdk/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.fos</groupId>
    <artifactId>football-os-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>fos-sdk</artifactId>
  <packaging>pom</packaging>
  <name>FOS SDK</name>

  <modules>
    <module>sdk-core</module>
    <module>sdk-events</module>
    <module>sdk-security</module>
    <module>sdk-storage</module>
    <module>sdk-canonical</module>
    <module>sdk-policy</module>
    <module>sdk-test</module>
  </modules>
</project>
```

- [ ] **Step 3: Create stub pom.xml for each SDK module (sdk-core only shown — repeat pattern for others)**

```xml
<!-- football-os-core/fos-sdk/sdk-core/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.fos</groupId>
    <artifactId>fos-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>sdk-core</artifactId>
  <name>FOS SDK — Core</name>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

Repeat for `sdk-events`, `sdk-security`, `sdk-storage`, `sdk-canonical`, `sdk-policy`, `sdk-test` — each starts as a minimal pom with only `spring-boot-starter-test` in test scope. Real dependencies added in Sprint 0.2.

- [ ] **Step 4: Create stub pom.xml for fos-governance-service**

```xml
<!-- football-os-core/fos-governance-service/pom.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.fos</groupId>
    <artifactId>football-os-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>fos-governance-service</artifactId>
  <name>FOS Governance Service</name>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

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

- [ ] **Step 5: Verify Maven build compiles**

```bash
cd football-os-core
mvn clean install -DskipTests
```

Expected: `BUILD SUCCESS` for all modules.

- [ ] **Step 6: Commit**

```bash
git add pom.xml fos-sdk/pom.xml fos-sdk/*/pom.xml fos-governance-service/pom.xml
git commit -m "chore: scaffold Maven multi-module structure for football-os-core"
```

---

## Task 2: Spring Boot Stubs — fos-governance-service and fos-gateway

**Files:**
- Create: `fos-governance-service/src/main/java/com/fos/governance/GovernanceApp.java`
- Create: `fos-governance-service/src/main/resources/application.yml`
- Create: `fos-gateway/src/main/java/com/fos/gateway/GatewayApp.java`
- Create: `fos-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Create `GovernanceApp.java`**

```java
// fos-governance-service/src/main/java/com/fos/governance/GovernanceApp.java
package com.fos.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GovernanceApp {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceApp.class, args);
    }
}
```

- [ ] **Step 2: Create `GatewayApp.java`**

```java
// fos-gateway/src/main/java/com/fos/gateway/GatewayApp.java
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

- [ ] **Step 3: Create `application.yml` for fos-governance-service**

```yaml
# fos-governance-service/src/main/resources/application.yml
spring:
  application:
    name: fos-governance-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/fos_governance
    username: ${DB_USER:fos}
    password: ${DB_PASS:fos}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

- [ ] **Step 4: Create `application.yml` for fos-gateway**

```yaml
# fos-gateway/src/main/resources/application.yml
spring:
  application:
    name: fos-gateway
  cloud:
    gateway:
      routes:
        - id: governance
          uri: http://fos-governance-service:8081
          predicates:
            - Path=/api/**

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

For gateway `pom.xml`, add Spring Cloud Gateway dependency. Add Spring Cloud BOM to parent pom `dependencyManagement`:

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

```xml
<!-- In parent pom.xml dependencyManagement -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-dependencies</artifactId>
  <version>2023.0.3</version>
  <type>pom</type>
  <scope>import</scope>
</dependency>
```

Port assignments (revised):
- `fos-gateway`: 8080
- `fos-governance-service`: 8081

- [ ] **Step 5: Verify each service compiles and starts in isolation**

```bash
cd fos-governance-service
mvn spring-boot:run
# expect: Started GovernanceApp in X seconds
# curl http://localhost:8081/actuator/health → {"status":"UP"}
```

(Services will fail to start if DB not up — that's fine, Docker Compose fixes that in Task 3.)

- [ ] **Step 6: Commit**

```bash
git add fos-governance-service/src fos-gateway/src
git commit -m "chore: add Spring Boot stubs for fos-governance-service and fos-gateway"
```

---

## Task 3: Docker Compose — Full Local Stack

**Files:**
- Create: `football-os-core/docker-compose.yml`
- Create: `football-os-core/.env.example`

- [ ] **Step 1: Write `docker-compose.yml`**

```yaml
# football-os-core/docker-compose.yml
version: "3.9"

services:

  postgres:
    image: postgres:16.3
    container_name: fos-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: fos_governance
      POSTGRES_USER: fos
      POSTGRES_PASSWORD: fos
    volumes:
      - fos-pg-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U fos -d fos_governance"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:7.0.12
    container_name: fos-mongodb
    ports:
      - "27017:27017"
    volumes:
      - fos-mongo-data:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.7.0
    container_name: fos-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.7.0
    container_name: fos-kafka
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:29092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT_INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 15s
      timeout: 10s
      retries: 5

  keycloak:
    image: quay.io/keycloak/keycloak:24.0.5
    container_name: fos-keycloak
    ports:
      - "8180:8080"
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/fos_governance
      KC_DB_USERNAME: fos
      KC_DB_PASSWORD: fos
      KC_DB_SCHEMA: fos_keycloak
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_HOSTNAME_STRICT: "false"
      KC_HTTP_ENABLED: "true"
    command: start-dev
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/health/ready || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 10

  minio:
    image: minio/minio:RELEASE.2024-08-03T04-33-23Z
    container_name: fos-minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - fos-minio-data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 10s
      timeout: 5s
      retries: 5

  opensearch:
    image: opensearchproject/opensearch:2.16.0
    container_name: fos-opensearch
    ports:
      - "9200:9200"
      - "9600:9600"
    environment:
      discovery.type: single-node
      OPENSEARCH_INITIAL_ADMIN_PASSWORD: FosAdmin!2024
      plugins.security.disabled: "true"
    volumes:
      - fos-opensearch-data:/usr/share/opensearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5

  redis:
    image: redis:7.2.5-alpine
    container_name: fos-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  fos-pg-data:
  fos-mongo-data:
  fos-minio-data:
  fos-opensearch-data:
```

- [ ] **Step 2: Create `.env.example`**

```dotenv
# football-os-core/.env.example
DB_HOST=localhost
DB_USER=fos
DB_PASS=fos
KEYCLOAK_URL=http://localhost:8180
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_URL=redis://localhost:6379
```

- [ ] **Step 3: Start the stack and verify all containers are healthy**

```bash
cd football-os-core
docker compose up -d
docker compose ps
```

Expected: all containers show `healthy` or `running` status within 60 seconds.

```bash
# Verify each component
curl http://localhost:8180/health/ready          # Keycloak
curl http://localhost:9200/_cluster/health       # OpenSearch
curl http://localhost:9000/minio/health/live     # MinIO
docker exec fos-postgres pg_isready -U fos       # PostgreSQL
docker exec fos-mongodb mongosh --eval "db.adminCommand('ping')" # MongoDB
docker exec fos-kafka kafka-topics --bootstrap-server localhost:9092 --list # Kafka
```

- [ ] **Step 4: Start all service skeletons against the local stack**

```bash
# From project root, in separate terminals:
cd fos-governance-service && mvn spring-boot:run &
cd fos-gateway && mvn spring-boot:run &
```

Verify health on each:
```bash
for port in 8080 8081; do
  echo "Port $port:" && curl -s http://localhost:$port/actuator/health | jq .status
done
```

Expected: `"UP"` for both.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .env.example
git commit -m "chore: add docker-compose for full local dev stack (postgres, mongo, kafka, keycloak, minio, opensearch, redis)"
```

---

## Task 4: CI Pipeline

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Write the CI pipeline**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16.3
        env:
          POSTGRES_DB: fos_governance
          POSTGRES_USER: fos
          POSTGRES_PASSWORD: fos
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      mongodb:
        image: mongo:7.0.12
        ports:
          - 27017:27017

      kafka:
        image: confluentinc/cp-kafka:7.7.0
        env:
          KAFKA_BROKER_ID: 1
          KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
          KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092
          KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT
          KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
          KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
          KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
        ports:
          - 9092:9092

      zookeeper:
        image: confluentinc/cp-zookeeper:7.7.0
        env:
          ZOOKEEPER_CLIENT_PORT: 2181

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build all modules (skip tests first)
        run: mvn clean install -DskipTests --no-transfer-progress

      - name: Run unit tests
        run: mvn test --no-transfer-progress
        env:
          DB_HOST: localhost
          DB_USER: fos
          DB_PASS: fos
          KAFKA_BOOTSTRAP_SERVERS: localhost:9092

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/target/surefire-reports/*.xml'
```

- [ ] **Step 2: Push to GitHub and verify the pipeline passes**

```bash
git add .github/
git commit -m "chore: add GitHub Actions CI pipeline"
git push origin main
```

Open the Actions tab and confirm the build passes with `BUILD SUCCESS`.

- [ ] **Step 3: Add `.gitignore`**

```gitignore
# football-os-core/.gitignore
target/
*.class
*.jar
!**/src/main/**/*.jar
.env
.idea/
*.iml
*.iws
*.ipr
.DS_Store
*.log
```

```bash
git add .gitignore
git commit -m "chore: add .gitignore"
```

---

## Sprint 0.1 Done — Verification Checklist

- [ ] `mvn clean install -DskipTests` passes from the root
- [ ] `docker compose up -d` brings up 9 healthy infrastructure containers (postgres, mongodb, zookeeper, kafka, keycloak, minio, opensearch, redis)
- [ ] `curl http://localhost:8081/actuator/health` returns `{"status":"UP"}` (and same for fos-gateway at 8080)
- [ ] GitHub Actions CI pipeline passes on `main`
- [ ] No business logic anywhere — pure infrastructure scaffold

**Next:** Sprint 0.2 — SDK Base Modules (`sdk-core`, `sdk-events`, `sdk-security`, `sdk-storage`)

---

## Amendment E — Architecture Revision: Revised Module List (supersedes Task 2)

Replace the original Task 2 (which scaffolded 6 separate services) with the following.

### E.1 Revised Maven module list in parent pom.xml

```xml
<modules>
  <module>fos-sdk</module>
  <module>fos-governance-service</module>
  <module>fos-gateway</module>
</modules>
```

Remove: `fos-identity-service`, `fos-canonical-service`, `fos-policy-service`, `fos-transmission-service`, `fos-signal-service`

### E.2 Add sdk-storage to SDK aggregator

In `fos-sdk/pom.xml`, add `<module>sdk-storage</module>` after `sdk-security`:

```xml
<modules>
  <module>sdk-core</module>
  <module>sdk-events</module>
  <module>sdk-security</module>
  <module>sdk-storage</module>
  <module>sdk-policy</module>
  <module>sdk-canonical</module>
  <module>sdk-test</module>
</modules>
```

Create stub `fos-sdk/sdk-storage/pom.xml` following the same pattern as other SDK modules.

### E.3 Create fos-governance-service stub

```java
// fos-governance-service/src/main/java/com/fos/governance/GovernanceApp.java
package com.fos.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GovernanceApp {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceApp.class, args);
    }
}
```

```yaml
# fos-governance-service/src/main/resources/application.yml
spring:
  application:
    name: fos-governance-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/fos_governance
    username: ${DB_USER:fos}
    password: ${DB_PASS:fos}
  jpa:
    hibernate:
      ddl-auto: validate

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

`fos-governance-service/pom.xml` — same pattern as original service stubs but with `artifactId=fos-governance-service`.

### E.4 Port assignments (revised)

- `fos-gateway`: 8080
- `fos-governance-service`: 8081

### E.5 Verification (replaces original Task 2 Step 3)

```bash
for port in 8080 8081; do
  echo "Port $port:" && curl -s http://localhost:$port/actuator/health | jq .status
done
```

Expected: `"UP"` for both.

### E.6 Revised CI services

Remove the Kafka-without-ZooKeeper KRaft config from the original CI YAML — use the ZooKeeper-based config from docker-compose consistently. The CI `build` job runs with the same infra as local dev.

---

## Amendment A — Issue #3: Dead Letter Queue + Issue #6: Rate Limiting Redis

### A.1 Add Redis to docker-compose.yml

Redis is needed for two concerns:
- Gateway rate limiting (Issue #6): `RequestRateLimiter` filter in Spring Cloud Gateway requires Redis
- Optional future caching (CanonicalResolver, Sprint 0.4+)

Add to `docker-compose.yml` services block:

```yaml
  redis:
    image: redis:7.2.5-alpine
    container_name: fos-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
```

Add `REDIS_URL=redis://localhost:6379` to `.env.example`.

### A.2 DLQ Topic Naming Convention

Add to the Kafka section of docker-compose comments and CI config. DLQ topics follow the pattern `{original-topic}.dlq`. Example:

```
fos.identity.actor.created        → fos.identity.actor.created.dlq
fos.canonical.player.created      → fos.canonical.player.created.dlq
fos.audit.all                     → fos.audit.all.dlq
```

DLQ topics are auto-created by Kafka when `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` (already set). No docker-compose change needed — but the naming convention must be documented in `KafkaTopics.java` (see Sprint 0.2 Amendment B).

### A.3 Update CI workflow — add Redis service

Add to `.github/workflows/ci.yml` services block:
```yaml
      redis:
        image: redis:7.2.5-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
```
