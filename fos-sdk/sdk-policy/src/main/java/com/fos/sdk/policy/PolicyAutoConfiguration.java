package com.fos.sdk.policy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
public class PolicyAutoConfiguration {

    @Bean
    public PolicyClient policyClient(RestClient.Builder builder,
            @org.springframework.beans.factory.annotation.Value(
                "${fos.policy.service-url:http://localhost:8081}") String url) {
        return new PolicyClient(builder, url);
    }

    @Bean
    public PolicyResultCache policyResultCache() {
        return new PolicyResultCache();
    }

    @Bean
    public PolicyGuardAspect policyGuardAspect(PolicyClient client,
                                                com.fos.sdk.security.FosSecurityContext ctx,
                                                PolicyResultCache cache) {
        return new PolicyGuardAspect(client, ctx, cache);
    }
}
