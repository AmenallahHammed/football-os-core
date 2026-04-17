package com.fos.sdk.policy;

import com.fos.sdk.canonical.CanonicalRef;

import java.util.Map;
import java.util.UUID;

/**
 * Input for a single policy evaluation.
 * Domain services create this via PolicyRequest.builder() or the static factories.
 */
public record PolicyRequest(
    UUID   actorId,
    String actorRole,
    String action,
    CanonicalRef resourceRef,
    String resourceState,
    Map<String, Object> context
) {
    /** Convenience builder-style factory for common case. */
    public static PolicyRequest of(UUID actorId, String actorRole,
                                   String action, CanonicalRef resourceRef,
                                   String resourceState) {
        return new PolicyRequest(actorId, actorRole, action, resourceRef, resourceState, Map.of());
    }

    public static PolicyRequest withContext(UUID actorId, String actorRole,
                                            String action, CanonicalRef resourceRef,
                                            String resourceState, Map<String, Object> context) {
        return new PolicyRequest(actorId, actorRole, action, resourceRef, resourceState, context);
    }
}
