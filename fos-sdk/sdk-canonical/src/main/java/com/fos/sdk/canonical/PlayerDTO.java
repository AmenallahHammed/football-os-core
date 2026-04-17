package com.fos.sdk.canonical;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only player identity from fos-governance-service/canonical.
 * Never store these fields in a domain entity — store only a CanonicalRef.
 */
public record PlayerDTO(
    UUID   id,
    String name,
    String position,
    String nationality,
    LocalDate dateOfBirth,
    UUID   currentTeamId
) {}
