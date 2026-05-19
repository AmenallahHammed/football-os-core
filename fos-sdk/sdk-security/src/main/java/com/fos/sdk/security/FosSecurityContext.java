package com.fos.sdk.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only way to get the current actor in a request context.
 * Never parse the JWT manually — always use this class.
 */
@Component
public class FosSecurityContext {
    private static final Pattern CANONICAL_CLUB_REF_PATTERN = Pattern.compile("^club:([0-9a-fA-F-]{36})$");

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
        Jwt jwt = jwt();
        return firstNonBlank(
                normalizeClubClaim(jwt.getClaimAsString("fos_club_id")),
                normalizeClubClaim(jwt.getClaimAsString("club_id")),
                normalizeClubClaim(jwt.getClaimAsString("clubId")),
                normalizeClubClaim(readNestedClaimAsString(jwt, "tenant", "clubId")));
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

    @SuppressWarnings("unchecked")
    private String readNestedClaimAsString(Jwt jwt, String parentClaim, String nestedClaim) {
        Object parent = jwt.getClaim(parentClaim);
        if (!(parent instanceof Map<?, ?> parentMap)) {
            return null;
        }
        Object value = parentMap.get(nestedClaim);
        return value instanceof String stringValue ? stringValue : null;
    }

    private String normalizeClubClaim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = CANONICAL_CLUB_REF_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
