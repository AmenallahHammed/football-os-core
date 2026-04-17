package com.fos.governance.policy.api;

import com.fos.governance.policy.application.PolicyEvaluationService;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/policy")
public class PolicyEvaluationController {

    private final PolicyEvaluationService evaluationService;

    public PolicyEvaluationController(PolicyEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/evaluate")
    public PolicyResult evaluate(@RequestBody PolicyEvaluationRequest request) {
        return evaluationService.evaluate(new PolicyRequest(
                request.actorId(), request.actorRole(), request.action(),
                request.resourceRef(), request.resourceState(),
                request.context() != null ? request.context() : java.util.Map.of()
        ));
    }
}
