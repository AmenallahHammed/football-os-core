package com.fos.sdk.policy;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.security.FosSecurityContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class PolicyGuardAspect {

    private final PolicyClient policyClient;
    private final FosSecurityContext securityContext;
    private final PolicyResultCache cache;

    public PolicyGuardAspect(PolicyClient policyClient,
                              FosSecurityContext securityContext,
                              PolicyResultCache cache) {
        this.policyClient = policyClient;
        this.securityContext = securityContext;
        this.cache = cache;
    }

    @Around("@annotation(guard)")
    public Object enforcePolicy(ProceedingJoinPoint joinPoint, PolicyGuard guard) throws Throwable {
        UUID actorId = securityContext.getActorId();
        String role  = securityContext.getRole();
        UUID resourceId = resolveResourceId(joinPoint, guard);
        CanonicalType resourceType = CanonicalType.valueOf(guard.resourceType());

        // Cache key includes actor, action, and resource state (UNKNOWN when state not resolvable here)
        String cacheKey = actorId + ":" + guard.action() + ":UNKNOWN";

        PolicyResult result = cache.getOrEvaluate(cacheKey, () ->
                policyClient.evaluate(PolicyRequest.of(
                        actorId, role, guard.action(),
                        CanonicalRef.of(resourceType, resourceId),
                        "UNKNOWN")));

        if (!result.isAllowed()) {
            throw new AccessDeniedException(result.reason());
        }

        return joinPoint.proceed();
    }

    private UUID resolveResourceId(ProceedingJoinPoint joinPoint, PolicyGuard guard) {
        Object[] args = joinPoint.getArgs();
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = sig.getParameterNames();

        if (paramNames != null && !guard.resourceIdParam().isEmpty()) {
            for (int i = 0; i < paramNames.length; i++) {
                if (paramNames[i].equals(guard.resourceIdParam()) && args[i] instanceof UUID) {
                    return (UUID) args[i];
                }
            }
        }

        // Fallback: first UUID parameter
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof UUID) return (UUID) arg;
            }
        }

        return UUID.randomUUID(); // no resource ID in args; policy will use context only
    }
}
