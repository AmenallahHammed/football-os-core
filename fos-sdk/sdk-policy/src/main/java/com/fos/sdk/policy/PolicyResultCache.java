package com.fos.sdk.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Caffeine-backed cache for PolicyResult entries.
 * Reduces OPA call volume for repeated policy checks within a request burst.
 *
 * Cache key: "{actorId}:{action}:{resourceState}"
 * TTL: 30 seconds — short enough that role/state changes propagate quickly.
 * Max size: 1000 entries — enough for hundreds of concurrent actors.
 */
@Component
public class PolicyResultCache {

    private final Cache<String, PolicyResult> cache;

    public PolicyResultCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Returns cached result if present, otherwise calls evaluator and caches the result.
     *
     * @param cacheKey  format: "{actorId}:{action}:{resourceState}"
     * @param evaluator called only on cache miss
     */
    public PolicyResult getOrEvaluate(String cacheKey, Supplier<PolicyResult> evaluator) {
        return cache.get(cacheKey, k -> evaluator.get());
    }

    /**
     * Evict all cache entries for a specific actor.
     * Called when the actor's role or state changes.
     */
    public void invalidateActor(String actorId) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(actorId + ":"));
    }

    /** Evict everything. Used in tests. */
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
