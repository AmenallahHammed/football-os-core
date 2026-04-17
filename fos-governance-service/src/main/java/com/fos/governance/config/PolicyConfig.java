package com.fos.governance.config;

import com.fos.governance.policy.application.context.*;
import com.fos.governance.policy.infrastructure.opa.OpaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfig {

    @Bean
    public OpaEvaluator opaEvaluator(OpaClient opaClient) {
        // Assemble the Chain of Responsibility: Role → ResourceState
        RoleContextBuilder roleBuilder = new RoleContextBuilder();
        ResourceStateContextBuilder stateBuilder = new ResourceStateContextBuilder();
        roleBuilder.then(stateBuilder);
        return new OpaEvaluator(opaClient, roleBuilder);
    }
}
