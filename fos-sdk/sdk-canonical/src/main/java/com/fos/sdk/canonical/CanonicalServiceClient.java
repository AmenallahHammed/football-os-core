package com.fos.sdk.canonical;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Remote Proxy for canonical football entity reads.
 * Hides the HTTP call to fos-governance-service from all domain code.
 * Prefer CanonicalResolver (caching proxy) over this class in domain services.
 */
@Component
public class CanonicalServiceClient {

    private final RestClient restClient;

    public CanonicalServiceClient(
            RestClient.Builder builder,
            @Value("${fos.canonical.service-url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public PlayerDTO getPlayer(UUID id) {
        return restClient.get()
                .uri("/api/v1/players/{id}", id)
                .retrieve()
                .body(PlayerDTO.class);
    }

    public TeamDTO getTeam(UUID id) {
        return restClient.get()
                .uri("/api/v1/teams/{id}", id)
                .retrieve()
                .body(TeamDTO.class);
    }

    /**
     * Used by fos-ingest-service to deduplicate players before creating new canonical entries.
     */
    public Optional<PlayerDTO> findByIdentity(String name, LocalDate dob, String nationality) {
        try {
            PlayerDTO result = restClient.get()
                    .uri(b -> b.path("/api/v1/players/find")
                                .queryParam("name", name)
                                .queryParam("dob", dob)
                                .queryParam("nationality", nationality)
                                .build())
                    .retrieve()
                    .body(PlayerDTO.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Used by fos-ingest-service to deduplicate teams before creating new canonical entries.
     */
    public Optional<TeamDTO> findTeamByName(String name, String country) {
        try {
            TeamDTO result = restClient.get()
                    .uri(b -> b.path("/api/v1/teams/find")
                                .queryParam("name", name)
                                .queryParam("country", country)
                                .build())
                    .retrieve()
                    .body(TeamDTO.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
