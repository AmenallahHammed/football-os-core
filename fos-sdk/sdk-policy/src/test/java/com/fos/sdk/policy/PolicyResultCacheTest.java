package com.fos.sdk.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyResultCacheTest {

    private PolicyResultCache cache;

    @BeforeEach
    void setUp() {
        cache = new PolicyResultCache();
    }

    @Test
    void should_call_evaluator_on_first_access() {
        AtomicInteger callCount = new AtomicInteger(0);

        PolicyResult result = cache.getOrEvaluate("actor1:read:ACTIVE", () -> {
            callCount.incrementAndGet();
            return PolicyResult.allow();
        });

        assertThat(result.isAllowed()).isTrue();
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void should_use_cache_on_second_access() {
        AtomicInteger callCount = new AtomicInteger(0);

        cache.getOrEvaluate("actor1:read:ACTIVE", () -> {
            callCount.incrementAndGet();
            return PolicyResult.allow();
        });
        cache.getOrEvaluate("actor1:read:ACTIVE", () -> {
            callCount.incrementAndGet();
            return PolicyResult.allow();
        });

        assertThat(callCount.get()).isEqualTo(1); // evaluator called only once
    }

    @Test
    void should_invalidate_all_entries_for_actor() {
        AtomicInteger callCount = new AtomicInteger(0);

        cache.getOrEvaluate("actor2:read:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.allow(); });
        cache.getOrEvaluate("actor2:write:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.deny("denied"); });

        cache.invalidateActor("actor2");

        cache.getOrEvaluate("actor2:read:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.allow(); });
        cache.getOrEvaluate("actor2:write:ACTIVE", () -> { callCount.incrementAndGet(); return PolicyResult.deny("denied"); });

        assertThat(callCount.get()).isEqualTo(4); // 2 original + 2 after invalidation
    }
}
