package com.fos.governance;

import com.fos.sdk.canonical.CanonicalAutoConfiguration;
import com.fos.sdk.canonical.CanonicalServiceClient;
import com.fos.sdk.policy.PolicyAutoConfiguration;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.test.FosTestContainersBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "fos.canonical.service-url=http://localhost:8081",
                "fos.policy.service-url=http://localhost:8081"
        })
@ImportAutoConfiguration({CanonicalAutoConfiguration.class, PolicyAutoConfiguration.class})
@Import(FosSecurityContext.class)
class SdkClientWiringTest extends FosTestContainersBase {

    @Autowired
    private CanonicalServiceClient canonicalServiceClient;

    @Autowired
    private PolicyClient policyClient;

    @Test
    void should_wire_sdk_clients_in_governance_context() {
        assertThat(canonicalServiceClient).isNotNull();
        assertThat(policyClient).isNotNull();
    }
}
