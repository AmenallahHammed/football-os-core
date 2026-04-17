package com.fos.sdk.policy;

import com.fos.sdk.security.FosSecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {PolicyGuardAspect.class, PolicyGuardAspectTest.TestService.class,
                            PolicyResultCache.class})
@org.springframework.context.annotation.EnableAspectJAutoProxy
class PolicyGuardAspectTest {

    @MockBean
    private PolicyClient policyClient;

    @MockBean
    private FosSecurityContext securityContext;

    @Autowired
    private TestService testService;

    @Test
    void should_allow_when_policy_returns_allow() {
        when(securityContext.getActorId()).thenReturn(UUID.randomUUID());
        when(securityContext.getRole()).thenReturn("PLAYER");
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.allow());

        String result = testService.protectedOperation();

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void should_throw_access_denied_when_policy_returns_deny() {
        when(securityContext.getActorId()).thenReturn(UUID.randomUUID());
        when(securityContext.getRole()).thenReturn("PLAYER");
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.deny("Role not allowed"));

        assertThatThrownBy(() -> testService.protectedOperation())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Role not allowed");
    }

    @Service
    static class TestService {
        @PolicyGuard(action = "test.resource.read", resourceType = "TEAM")
        public String protectedOperation() {
            return "ok";
        }
    }
}
