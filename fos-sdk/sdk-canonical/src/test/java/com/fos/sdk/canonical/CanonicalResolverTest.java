package com.fos.sdk.canonical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CanonicalResolverTest {

    private CanonicalServiceClient mockClient;
    private CanonicalResolver resolver;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(CanonicalServiceClient.class);
        resolver = new CanonicalResolver(mockClient);
    }

    @Test
    void should_return_player_from_client_on_first_call() {
        UUID id = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(id, "Test Player", "ST", "ES",
                java.time.LocalDate.of(1995, 5, 5), null);
        when(mockClient.getPlayer(id)).thenReturn(dto);

        PlayerDTO result = resolver.getPlayer(id);

        assertThat(result.name()).isEqualTo("Test Player");
        verify(mockClient, times(1)).getPlayer(id);
    }

    @Test
    void should_return_player_from_cache_on_second_call() {
        UUID id = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(id, "Cached Player", "CM", "BR",
                java.time.LocalDate.of(1998, 3, 3), null);
        when(mockClient.getPlayer(id)).thenReturn(dto);

        resolver.getPlayer(id); // populates cache
        resolver.getPlayer(id); // should hit cache

        // client called only once despite two resolver calls
        verify(mockClient, times(1)).getPlayer(id);
    }

    @Test
    void should_evict_player_cache_entry() {
        UUID id = UUID.randomUUID();
        PlayerDTO dto = new PlayerDTO(id, "Evict Player", "GK", "DE",
                java.time.LocalDate.of(2000, 1, 1), null);
        when(mockClient.getPlayer(id)).thenReturn(dto);

        resolver.getPlayer(id);
        resolver.evict(CanonicalRef.player(id));
        resolver.getPlayer(id); // must call client again after eviction

        verify(mockClient, times(2)).getPlayer(id);
    }
}
