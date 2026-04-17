package com.fos.sdk.canonical;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caching Proxy over CanonicalServiceClient.
 * Domain services inject CanonicalResolver — never CanonicalServiceClient directly.
 *
 * Cache is in-memory (ConcurrentHashMap). Redis-backed caching is added in Phase 1+
 * when canonical resolution becomes a measured bottleneck.
 *
 * Cache entries are evicted explicitly (on FACT signals from governance) or on-demand.
 * There is no TTL here — canonical data rarely changes.
 */
@Component
public class CanonicalResolver {

    private final CanonicalServiceClient client;
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public CanonicalResolver(CanonicalServiceClient client) {
        this.client = client;
    }

    public PlayerDTO getPlayer(UUID id) {
        String key = "player:" + id;
        return (PlayerDTO) cache.computeIfAbsent(key, k -> client.getPlayer(id));
    }

    public TeamDTO getTeam(UUID id) {
        String key = "team:" + id;
        return (TeamDTO) cache.computeIfAbsent(key, k -> client.getTeam(id));
    }

    /**
     * Evict a cache entry when a FACT signal indicates the canonical entity was updated.
     * Called by domain services that consume canonical FACT signals.
     */
    public void evict(CanonicalRef ref) {
        String key = ref.type().name().toLowerCase() + ":" + ref.id();
        cache.remove(key);
    }

    /** Evict all entries. Useful for tests. */
    public void evictAll() {
        cache.clear();
    }
}
