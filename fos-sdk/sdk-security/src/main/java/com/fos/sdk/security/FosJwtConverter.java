package com.fos.sdk.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        addRoles(roles, jwt.getClaim("roles"));
        addRoles(roles, readNestedClaim(jwt, "realm_access", "roles"));
        addRoles(roles, readNestedClaim(jwt, "resource_access", "fos-workspace-frontend", "roles"));
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private void addRoles(LinkedHashSet<String> roles, Object claimValue) {
        if (!(claimValue instanceof List<?> claimRoles)) {
            return;
        }
        claimRoles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .forEach(roles::add);
    }

    private Object readNestedClaim(Jwt jwt, String... claimPath) {
        Object current = jwt.getClaims();
        for (String pathSegment : claimPath) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(pathSegment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
