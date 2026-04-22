# fos-gateway-service Documentation

## Purpose of this folder

`fos-gateway-service` is a **minimal Spring Boot gateway skeleton**.

Its role is to provide a starting point for an API gateway process (an edge service that can later handle routing, authentication, and request controls). In its current form, this module only contains the bootstrap entry point and dependency setup.

## Important context in this repository

At repository level, the active gateway module listed in the root Maven build is `fos-gateway` (not `fos-gateway-service`).

- Root modules are declared in `pom.xml` and include `fos-gateway`.
- `fos-gateway-service` currently appears standalone and is not listed as a root module.

So this folder is best understood as a lightweight/legacy scaffold, while `fos-gateway` is the fuller gateway implementation used by the backend architecture.

## Folder contents and file purposes

### Core source files

- `fos-gateway-service/src/main/java/com/fos/gateway/GatewayApp.java`
  - Main application class annotated with `@SpringBootApplication`.
  - Calls `SpringApplication.run(...)` to start the service.
  - This is the runtime entry point.

### Build and dependency files

- `fos-gateway-service/pom.xml`
  - Maven module definition.
  - Declares parent project `football-os-core`.
  - Adds gateway-related dependencies:
    - `spring-cloud-starter-gateway` (reactive API gateway framework)
    - `spring-boot-starter-oauth2-resource-server` (JWT resource-server support)
    - `spring-boot-starter-data-redis-reactive` (reactive Redis client, commonly used for rate limiting/caching)
    - `spring-boot-starter-actuator` (health and metrics endpoints)
    - test and Lombok dependencies.

### IDE and generated files

- `fos-gateway-service/.classpath`, `.factorypath`, `.settings/*`
  - Eclipse/IDE metadata.
  - Not part of business/runtime logic.

- `fos-gateway-service/target/*`
  - Maven build output (compiled classes, packaged artifacts).
  - Auto-generated; not hand-maintained source code.

## How files in this folder work together

The runtime flow is currently simple:

1. Maven resolves dependencies from `pom.xml`.
2. `GatewayApp` starts Spring Boot auto-configuration.
3. Spring creates a gateway-capable app context using the classpath starters.

Because there are no explicit route/filter/security configuration classes in this folder today, behavior is mostly default framework behavior until additional config is added.

## How this folder interacts with the backend module (general view)

In the backend architecture, the gateway layer sits in front of domain services and typically handles cross-cutting concerns before forwarding traffic.

For this repository specifically:

- Architecture docs describe governance core as `fos-governance-service` + `fos-gateway`.
- Domain/backend services include modules like:
  - `fos-governance-service` (identity/canonical/policy/signal concerns)
  - `fos-workspace-service` (workspace domain capabilities)
- The operational gateway behavior (routing, auth policy, request enrichment, rate limiting) is implemented in `fos-gateway`.

So `fos-gateway-service` should be considered:

- a bootstrap module with gateway dependencies, and
- conceptually part of the gateway layer,
- but not the main integrated gateway module in the current multi-module build.

## If you want this folder to become the active gateway

Typical next steps would be:

1. Add it to root `pom.xml` `<modules>` (if intended to be built with the full backend).
2. Add `application.yml` with route, security, and redis settings.
3. Add route/filter/security config classes (similar to `fos-gateway`).
4. Add gateway tests for JWT, header propagation, and rate limiting.
