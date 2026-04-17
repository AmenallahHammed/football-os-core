package com.fos.sdk.canonical;

import java.util.UUID;

/**
 * Read-only team identity from fos-governance-service/canonical.
 * Never store these fields in a domain entity — store only a CanonicalRef.
 */
public record TeamDTO(
    UUID   id,
    String name,
    String shortName,
    String country,
    UUID   clubId
) {}
