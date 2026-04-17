# Issues & Fix Plan

## Status Key
- DONE: fixed in current branch
- TODO: still pending
- BLOCKED: cannot complete in code-only scope

| # | File(s) | Issue | Fix | Status |
|---|---|---|---|---|
| 1 | `fos-governance-service/src/main/java/com/fos/governance/canonical/domain/Player.java` + `fos-governance-service/src/main/resources/db/migration/V003__create_player_table.sql` | Player PK column mismatch (`player_id` vs `resource_id`) | Map entity ID to `resource_id` | DONE |
| 2 | `fos-governance-service/src/main/java/com/fos/governance/canonical/domain/Team.java` + `fos-governance-service/src/main/resources/db/migration/V004__create_team_table.sql` | Team PK column mismatch (`team_id` vs `resource_id`) | Map entity ID to `resource_id` | DONE |
| 3 | `fos-governance-service/src/main/resources/db/migration/V006__add_version_columns.sql` | Missing optimistic lock columns for JPA `@Version` fields | Add `version BIGINT NOT NULL DEFAULT 0` to actor/player/team tables | DONE |
| 4 | `fos-gateway/src/main/resources/application.yml` | Wrong default JWKS port (`8090`) | Use `8180` (`KEYCLOAK_JWKS_URI` default) | DONE |
| 5 | `fos-governance-service/src/main/java/com/fos/governance/identity/api/KeycloakWebhookController.java` | Webhook accepted unverified payloads | Require `X-Keycloak-Webhook-Secret`; reject unauthorized; constant-time compare | DONE |
| 6 | `fos-governance-service/src/main/resources/application.yml` | No config key for webhook shared secret | Add `fos.identity.keycloak.webhook.secret` from `KEYCLOAK_WEBHOOK_SECRET` | DONE |
| 7 | `docker-compose.yml` | Hardcoded Keycloak admin credentials | Use env vars `KEYCLOAK_ADMIN` and `KEYCLOAK_ADMIN_PASSWORD` | DONE |
| 8 | `docker-compose.yml` | Hardcoded OpenSearch admin password | Use env var `OPENSEARCH_INITIAL_ADMIN_PASSWORD` | DONE |
| 9 | `.env.example` | Missing env placeholders for required secrets | Add `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `OPENSEARCH_INITIAL_ADMIN_PASSWORD`, `KEYCLOAK_WEBHOOK_SECRET` | DONE |
| 10 | `.gitignore` and tracked generated outputs | Build artifacts committed (`bin/**`, Eclipse metadata) | Add ignores (`bin/`, `**/bin/`, `.project`, `.settings/`) and remove generated files from workspace | DONE |
| 11 | `fos-gateway/pom.xml` | WireMock classpath conflict (`spring-cloud-contract-wiremock` + `wiremock-standalone:3.5.4`) caused `NoSuchMethodError` | Remove direct `wiremock-standalone` test dependency and keep Spring Cloud Contract WireMock managed version | DONE |
| 12 | Local Docker/Testcontainers runtime | Testcontainers cannot discover valid Docker API (Npipe strategy returns 400 / empty daemon info) | Fix local Docker/Testcontainers environment before integration tests can pass | BLOCKED |

## Session Plan

### Session 1 - Secrets Baseline (DONE)
1. Update `.env.example` with required secret/env keys.
2. Run `docker-compose config` to verify interpolation.
3. Run `docker-compose up` to verify startup.

### Session 2 - Repo Hygiene (DONE)
1. Update `.gitignore` for generated artifacts.
2. Remove generated files from workspace.
3. Confirm no `bin/**` files remain.

### Session 3 - Verification (BLOCKED)
1. Run governance tests: `mvn -pl fos-governance-service test`.
2. Run gateway tests: `mvn -pl fos-gateway test`.
3. Smoke run compose stack again.

Current result:
- `docker-compose` smoke check: healthy.
- `fos-gateway` tests: JWT + correlation tests pass after WireMock fix; rate-limit test still blocked by Testcontainers Docker detection.
- `fos-governance-service` tests: integration/e2e tests blocked by same Testcontainers Docker detection.

## Required Runtime Environment Variables
- `KEYCLOAK_ADMIN`
- `KEYCLOAK_ADMIN_PASSWORD`
- `OPENSEARCH_INITIAL_ADMIN_PASSWORD`
- `KEYCLOAK_WEBHOOK_SECRET`
