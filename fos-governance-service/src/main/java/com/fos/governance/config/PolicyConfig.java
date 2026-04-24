package com.fos.governance.config;

import com.fos.governance.policy.application.context.*;
import com.fos.governance.policy.infrastructure.opa.OpaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfig {

    @Bean
    public OpaEvaluator opaEvaluator(OpaClient opaClient) {
        // Assemble the Chain of Responsibility: Role -> ResourceState -> RequestContext
        RoleContextBuilder roleBuilder = new RoleContextBuilder();
        ResourceStateContextBuilder stateBuilder = new ResourceStateContextBuilder();
        RequestContextBuilder requestContextBuilder = new RequestContextBuilder();
        roleBuilder.then(stateBuilder).then(requestContextBuilder);
        return new OpaEvaluator(opaClient, roleBuilder);
    }
}
