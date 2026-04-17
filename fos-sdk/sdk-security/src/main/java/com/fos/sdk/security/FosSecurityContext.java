package com.fos.sdk.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * The only way to get the current actor in a request context.
 * Never parse the JWT manually — always use this class.
 */
@Component
public class FosSecurityContext {

    public java.util.UUID getActorId() {
        return java.util.UUID.fromString(jwt().getSubject());
    }

    public String getRole() {
        List<String> r = roles();
        return r.isEmpty() ? "UNKNOWN" : r.get(0);
    }

    public String actorId() {
        return jwt().getSubject();
    }

    public String clubId() {
        return jwt().getClaimAsString("fos_club_id");
    }

    @SuppressWarnings("unchecked")
    public List<String> roles() {
        Object roles = jwt().getClaim("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public boolean hasRole(String role) {
        return roles().contains(role);
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new IllegalStateException("No authenticated actor in security context");
    }
}
