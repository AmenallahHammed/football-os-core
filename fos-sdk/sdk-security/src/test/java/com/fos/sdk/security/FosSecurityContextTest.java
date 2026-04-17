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
    void should_return_true_when_actor_has_role() {
        setUpSecurityContext("actor-001", "club-001", List.of("ROLE_HEAD_COACH", "ROLE_ASSISTANT_COACH"));

        assertThat(context.hasRole("ROLE_HEAD_COACH")).isTrue();
        assertThat(context.hasRole("ROLE_PLAYER")).isFalse();
    }

    @Test
    void should_throw_when_no_security_context() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(context::actorId)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No authenticated actor");
    }

    private void setUpSecurityContext(String actorId, String clubId, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", actorId)
            .claim("fos_club_id", clubId)
            .claim("roles", roles)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        var auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
