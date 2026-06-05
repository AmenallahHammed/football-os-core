package com.fos.sdk.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashSet;
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
    private static final Pattern APPLICATION_ROLE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public java.util.UUID getActorId() {
        return java.util.UUID.fromString(jwt().getSubject());
    }

    public String getRole() {
        List<String> r = roles();
        return r.stream()
                .filter(role -> role != null && role.startsWith("ROLE_"))
                .findFirst()
                .orElseGet(() -> r.isEmpty() ? "UNKNOWN" : r.get(0));
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
        Jwt jwt = jwt();
        LinkedHashSet<String> resolvedRoles = new LinkedHashSet<>();
        addRoles(resolvedRoles, jwt.getClaim("roles"));
        addRoles(resolvedRoles, readNestedClaim(jwt, "realm_access", "roles"));
        addRoles(resolvedRoles, readNestedClaim(jwt, "resource_access", "fos-workspace-frontend", "roles"));
        return List.copyOf(resolvedRoles);
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
        Object value = readNestedClaim(jwt, parentClaim, nestedClaim);
        return value instanceof String stringValue ? stringValue : null;
    }

    @SuppressWarnings("unchecked")
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

    private void addRoles(LinkedHashSet<String> roles, Object claimValue) {
        if (!(claimValue instanceof List<?> claimRoles)) {
            return;
        }
        for (Object role : claimRoles) {
            if (role instanceof String roleValue) {
                String trimmed = roleValue.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                roles.add(trimmed);

                String normalized = normalizeApplicationRole(trimmed);
                if (!normalized.equals(trimmed)) {
                    roles.add(normalized);
                }
            }
        }
    }

    private String normalizeApplicationRole(String role) {
        if (role.startsWith("ROLE_")) {
            return role;
        }
        if (!APPLICATION_ROLE_PATTERN.matcher(role).matches()) {
            return role;
        }
        return "ROLE_" + role;
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
