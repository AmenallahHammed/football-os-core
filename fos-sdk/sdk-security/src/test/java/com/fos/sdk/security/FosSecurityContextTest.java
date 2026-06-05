package com.fos.sdk.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class FosSecurityContextTest {

    private final FosSecurityContext context = new FosSecurityContext();

    @Test
    void should_extract_actor_id_from_jwt() {
        String actorId = UUID.randomUUID().toString();
        setUpSecurityContext(actorId, "test-club-001", List.of("ROLE_HEAD_COACH"));

        assertThat(context.actorId()).isEqualTo(actorId);
    }

    @Test
    void should_extract_club_id_from_jwt() {
        setUpSecurityContext("actor-001", "club-999", List.of("ROLE_PLAYER"));

        assertThat(context.clubId()).isEqualTo("club-999");
    }

    @Test
    void should_extract_club_id_from_legacy_claim_name() {
        setUpSecurityContextWithClaims("actor-001", List.of("ROLE_PLAYER"),
                java.util.Map.of("club_id", "11111111-1111-1111-1111-111111111111"));

        assertThat(context.clubId()).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void should_extract_club_id_from_camel_case_claim_name() {
        setUpSecurityContextWithClaims("actor-001", List.of("ROLE_PLAYER"),
                java.util.Map.of("clubId", "22222222-2222-2222-2222-222222222222"));

        assertThat(context.clubId()).isEqualTo("22222222-2222-2222-2222-222222222222");
    }

    @Test
    void should_extract_club_id_from_tenant_claim() {
        setUpSecurityContextWithClaims("actor-001", List.of("ROLE_PLAYER"),
                java.util.Map.of("tenant", java.util.Map.of("clubId", "33333333-3333-3333-3333-333333333333")));

        assertThat(context.clubId()).isEqualTo("33333333-3333-3333-3333-333333333333");
    }

    @Test
    void should_normalize_canonical_club_reference() {
        setUpSecurityContextWithClaims("actor-001", List.of("ROLE_PLAYER"),
                java.util.Map.of("fos_club_id", "club:44444444-4444-4444-4444-444444444444"));

        assertThat(context.clubId()).isEqualTo("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void should_return_true_when_actor_has_role() {
        setUpSecurityContext("actor-001", "club-001", List.of("ROLE_HEAD_COACH", "ROLE_ASSISTANT_COACH"));

        assertThat(context.hasRole("ROLE_HEAD_COACH")).isTrue();
        assertThat(context.hasRole("ROLE_PLAYER")).isFalse();
    }

    @Test
    void should_extract_roles_from_realm_access_claim() {
        setUpSecurityContextWithClaims("actor-001", List.of(),
                java.util.Map.of(
                        "fos_club_id", "club-001",
                        "realm_access", java.util.Map.of("roles", List.of("ROLE_HEAD_COACH", "ROLE_ANALYST"))));

        assertThat(context.roles()).contains("ROLE_HEAD_COACH", "ROLE_ANALYST");
    }

    @Test
    void should_normalize_unprefixed_application_roles() {
        setUpSecurityContextWithClaims("actor-001", List.of(),
                java.util.Map.of(
                        "fos_club_id", "club-001",
                        "realm_access", java.util.Map.of("roles", List.of("HEAD_COACH", "ANALYST", "default-roles-fos"))));

        assertThat(context.roles()).contains("HEAD_COACH", "ROLE_HEAD_COACH", "ANALYST", "ROLE_ANALYST", "default-roles-fos");
        assertThat(context.getRole()).isEqualTo("ROLE_HEAD_COACH");
    }

    @Test
    void should_prefer_application_role_when_keycloak_emits_default_roles_first() {
        setUpSecurityContextWithClaims("actor-001", List.of(),
                java.util.Map.of(
                        "fos_club_id", "club-001",
                        "realm_access", java.util.Map.of(
                                "roles", List.of("HEAD_COACH", "default-roles-fos", "ROLE_HEAD_COACH"))));

        assertThat(context.getRole()).isEqualTo("ROLE_HEAD_COACH");
    }

    @Test
    void should_extract_roles_from_client_resource_access_claim() {
        setUpSecurityContextWithClaims("actor-001", List.of(),
                java.util.Map.of(
                        "fos_club_id", "club-001",
                        "resource_access", java.util.Map.of(
                                "fos-workspace-frontend", java.util.Map.of("roles", List.of("ROLE_CLUB_ADMIN")))));

        assertThat(context.roles()).contains("ROLE_CLUB_ADMIN");
    }

    @Test
    void should_throw_when_no_security_context() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(context::actorId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated actor");
    }

    private void setUpSecurityContext(String actorId, String clubId, List<String> roles) {
        setUpSecurityContextWithClaims(actorId, roles, java.util.Map.of("fos_club_id", clubId));
    }

    private void setUpSecurityContextWithClaims(String actorId, List<String> roles, java.util.Map<String, Object> claims) {
        Jwt.Builder jwtBuilder = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", actorId)
            .claim("roles", roles)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach((claimName, claimValue) -> jwtBuilder.claim(claimName, claimValue));
        Jwt builtJwt = jwtBuilder.build();
        var auth = new JwtAuthenticationToken(builtJwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
