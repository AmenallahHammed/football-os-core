package com.fos.sdk.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FosJwtConverterTest {

    private final FosJwtConverter converter = new FosJwtConverter();

    @Test
    void should_extract_roles_from_top_level_realm_and_client_claims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "actor-001")
                .claim("roles", List.of("ROLE_HEAD_COACH"))
                .claim("realm_access", Map.of("roles", List.of("ROLE_ANALYST")))
                .claim("resource_access", Map.of(
                        "fos-workspace-frontend", Map.of("roles", List.of("ROLE_CLUB_ADMIN"))))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_HEAD_COACH", "ROLE_ANALYST", "ROLE_CLUB_ADMIN");
    }

    @Test
    void should_keep_role_prefix_and_deduplicate_roles() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "actor-001")
                .claim("roles", List.of("ROLE_HEAD_COACH"))
                .claim("realm_access", Map.of("roles", List.of("ROLE_HEAD_COACH")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication =
                (JwtAuthenticationToken) converter.convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_HEAD_COACH");
    }
}
