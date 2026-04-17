package com.fos.governance.policy.application.context;

import com.fos.governance.policy.infrastructure.opa.OpaClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;

import java.util.Map;

public class OpaEvaluator {

    private final OpaClient opaClient;
    private final PolicyContextBuilder contextChain;

    public OpaEvaluator(OpaClient opaClient, PolicyContextBuilder contextChain) {
        this.opaClient = opaClient;
        this.contextChain = contextChain;
    }

    public PolicyResult evaluate(PolicyRequest request) {
        Map<String, Object> opaInput = contextChain.build(request);
        boolean allowed = opaClient.evaluate(opaInput);
        return allowed ? PolicyResult.allow() : PolicyResult.deny("Policy denied: " + request.action());
    }
}
