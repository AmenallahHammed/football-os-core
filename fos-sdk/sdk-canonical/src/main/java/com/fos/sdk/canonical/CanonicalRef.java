package com.fos.sdk.canonical;

import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Immutable foreign key to a canonical football entity.
 * Domain entities store this instead of copying canonical fields.
 * To resolve the display name, use CanonicalResolver.getPlayer(ref.id()).
 */
@Embeddable
public record CanonicalRef(CanonicalType type, UUID id) {

    public static CanonicalRef of(CanonicalType type, UUID id) {
        return new CanonicalRef(type, id);
    }

    public static CanonicalRef player(UUID id) {
        return new CanonicalRef(CanonicalType.PLAYER, id);
    }

    public static CanonicalRef team(UUID id) {
        return new CanonicalRef(CanonicalType.TEAM, id);
    }

    public static CanonicalRef club(UUID id) {
        return new CanonicalRef(CanonicalType.CLUB, id);
    }

    @Override
    public String toString() {
        return type.name() + ":" + id.toString();
    }

    public static CanonicalRef parse(String ref) {
        if (ref == null || !ref.contains(":")) {
            throw new IllegalArgumentException("Invalid CanonicalRef format: " + ref);
        }
        String[] parts = ref.split(":");
        return new CanonicalRef(CanonicalType.valueOf(parts[0]), UUID.fromString(parts[1]));
    }
}
