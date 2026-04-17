package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.Map;

public class ResourceStateContextBuilder extends PolicyContextBuilder {
    @Override
    protected Map<String, Object> enrich(PolicyRequest request) {
        return Map.of("resource", Map.of(
                "type",   request.resourceRef() != null ? request.resourceRef().type().name() : "",
                "id",     request.resourceRef() != null ? request.resourceRef().id().toString() : "",
                "state",  request.resourceState() != null ? request.resourceState() : "UNKNOWN",
                "action", request.action()
        ));
    }
}
