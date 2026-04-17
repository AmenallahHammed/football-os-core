package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalRef;
import java.util.UUID;

/**
 * A test actor: a consistent triple of (actorId, signedJwt, canonicalRef).
 */
public record TestActor(
    UUID        actorId,
    String      signedJwt,
    String      role,
    CanonicalRef canonicalRef
) {
    public String authorizationHeader() {
        return "Bearer " + signedJwt;
    }
}
