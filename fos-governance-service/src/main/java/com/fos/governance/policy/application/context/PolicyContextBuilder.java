package com.fos.governance.policy.application.context;

import com.fos.sdk.policy.PolicyRequest;

import java.util.HashMap;
import java.util.Map;

public abstract class PolicyContextBuilder {

    private PolicyContextBuilder next;

    public PolicyContextBuilder then(PolicyContextBuilder next) {
        this.next = next;
        return next;
    }

    public final Map<String, Object> build(PolicyRequest request) {
        Map<String, Object> context = new HashMap<>(enrich(request));
        if (next != null) {
            context.putAll(next.build(request));
        }
        return context;
    }

    protected abstract Map<String, Object> enrich(PolicyRequest request);
}
