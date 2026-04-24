package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.Map;

public class RequestContextBuilder extends PolicyContextBuilder {
    @Override
    protected Map<String, Object> enrich(PolicyRequest request) {
        return request.context() != null ? request.context() : Map.of();
    }
}
