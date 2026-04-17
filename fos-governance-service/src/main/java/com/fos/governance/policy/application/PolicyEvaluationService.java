package com.fos.governance.policy.application;

import com.fos.governance.policy.application.context.OpaEvaluator;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import org.springframework.stereotype.Service;

@Service
public class PolicyEvaluationService {

    private final OpaEvaluator opaEvaluator;

    public PolicyEvaluationService(OpaEvaluator opaEvaluator) {
        this.opaEvaluator = opaEvaluator;
    }

    public PolicyResult evaluate(PolicyRequest request) {
        return opaEvaluator.evaluate(request);
    }
}
