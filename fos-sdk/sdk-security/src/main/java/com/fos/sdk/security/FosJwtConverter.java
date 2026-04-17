package com.fos.sdk.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Collection;
import java.util.List;

/**
 * Converts a Keycloak JWT into a Spring Security authentication token.
 * Extracts FOS roles from the "roles" claim.
 */
public class FosJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<SimpleGrantedAuthority> authorities = extractRoles(jwt);
        return new JwtAuthenticationToken(jwt, authorities);
    }

    @SuppressWarnings("unchecked")
    private Collection<SimpleGrantedAuthority> extractRoles(Jwt jwt) {
        Object rolesClaim = jwt.getClaim("roles");
        if (!(rolesClaim instanceof List<?> roles)) return List.of();
        return roles.stream()
            .filter(String.class::isInstance)
            .map(r -> new SimpleGrantedAuthority((String) r))
            .toList();
    }
}
