package com.fos.sdk.policy;

/**
 * Result of a policy evaluation.
 * Domain services check result.isAllowed() before proceeding.
 */
public record PolicyResult(PolicyDecision decision, String reason) {

    public boolean isAllowed() {
        return decision == PolicyDecision.ALLOW;
    }

    public static PolicyResult allow() {
        return new PolicyResult(PolicyDecision.ALLOW, "allowed");
    }

    public static PolicyResult deny(String reason) {
        return new PolicyResult(PolicyDecision.DENY, reason);
    }
}
