package com.fos.governance.policy.api;

import com.fos.sdk.canonical.CanonicalRef;
import java.util.Map;
import java.util.UUID;

public record PolicyEvaluationRequest(
    UUID actorId,
    String actorRole,
    String action,
    CanonicalRef resourceRef,
    String resourceState,
    Map<String, Object> context
) {}
