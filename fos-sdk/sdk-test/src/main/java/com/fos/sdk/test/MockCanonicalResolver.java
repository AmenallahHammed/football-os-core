package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalResolver;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.canonical.TeamDTO;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Null Object / test stub for CanonicalResolver.
 */
public class MockCanonicalResolver extends CanonicalResolver {

    public MockCanonicalResolver() {
        super(null);
    }

    @Override
    public PlayerDTO getPlayer(UUID id) {
        return new PlayerDTO(
                id,
                "Test Player " + id.toString().substring(0, 8),
                "CF",
                "TS",
                LocalDate.of(1995, 1, 1),
                null
        );
    }

    @Override
    public TeamDTO getTeam(UUID id) {
        return new TeamDTO(
                id,
                "Test FC " + id.toString().substring(0, 8),
                "TFC",
                "TS",
                null
        );
    }

    @Override
    public void evict(com.fos.sdk.canonical.CanonicalRef ref) {}

    @Override
    public void evictAll() {}
}
