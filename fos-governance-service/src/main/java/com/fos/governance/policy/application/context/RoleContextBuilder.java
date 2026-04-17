package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.Map;

public class RoleContextBuilder extends PolicyContextBuilder {
    @Override
    protected Map<String, Object> enrich(PolicyRequest request) {
        return Map.of("actor", Map.of(
                "id",   request.actorId().toString(),
                "role", request.actorRole() != null ? request.actorRole() : ""
        ));
    }
}
