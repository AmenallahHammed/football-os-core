package com.fos.governance.canonical;

import com.fos.governance.canonical.api.PlayerRequest;
import com.fos.governance.canonical.api.TeamRequest;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.canonical.TeamDTO;
import com.fos.sdk.test.FosTestContainersBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CanonicalIntegrationTest extends FosTestContainersBase {

    private static String unique(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID();
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_create_player_and_return_201() {
        String playerName = unique("Lionel Test");
        PlayerRequest request = new PlayerRequest(
                playerName, "CF", "AR", LocalDate.of(1987, 6, 24), null);

        ResponseEntity<PlayerDTO> response = restTemplate.postForEntity(
                "/api/v1/players", request, PlayerDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo(playerName);
        assertThat(response.getBody().position()).isEqualTo("CF");
    }

    @Test
    void should_find_player_by_identity_for_dedup() {
        String playerName = unique("Dedup Player");
        PlayerRequest request = new PlayerRequest(
                playerName, "CM", "BR", LocalDate.of(1995, 3, 15), null);

        // Create player
        restTemplate.postForObject("/api/v1/players", request, PlayerDTO.class);

        // Find by identity (dedup endpoint)
        ResponseEntity<PlayerDTO> found = restTemplate.getForEntity(
                "/api/v1/players/find?name=" + playerName + "&dob=1995-03-15&nationality=BR",
                PlayerDTO.class);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().name()).isEqualTo(playerName);
    }

    @Test
    void should_not_find_nonexistent_player_by_identity() {
        String playerName = unique("Ghost Player");
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/players/find?name=" + playerName + "&dob=2000-01-01&nationality=XX",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_create_team_and_return_201() {
        String teamName = unique("Test FC");
        TeamRequest request = new TeamRequest(teamName, "TFC", "ES", null);

        ResponseEntity<TeamDTO> response = restTemplate.postForEntity(
                "/api/v1/teams", request, TeamDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo(teamName);
    }

    @Test
    void should_update_player() {
        String originalName = unique("Update Player");
        String updatedName = unique("Updated Player");
        PlayerRequest createRequest = new PlayerRequest(
                originalName, "CM", "PT", LocalDate.of(1998, 1, 10), null);

        PlayerDTO created = restTemplate.postForObject("/api/v1/players", createRequest, PlayerDTO.class);

        PlayerRequest updateRequest = new PlayerRequest(
                updatedName, "LW", "PT", LocalDate.of(1998, 1, 10), null);

        ResponseEntity<PlayerDTO> updated = restTemplate.exchange(
                "/api/v1/players/" + created.id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                PlayerDTO.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().name()).isEqualTo(updatedName);
        assertThat(updated.getBody().position()).isEqualTo("LW");
    }

    @Test
    void should_update_team() {
        String originalName = unique("Old Name FC");
        String updatedName = unique("New Name FC");
        TeamRequest createRequest = new TeamRequest(originalName, "ONF", "FR", null);

        TeamDTO created = restTemplate.postForObject("/api/v1/teams", createRequest, TeamDTO.class);

        TeamRequest updateRequest = new TeamRequest(updatedName, "NNF", "FR", null);

        ResponseEntity<TeamDTO> updated = restTemplate.exchange(
                "/api/v1/teams/" + created.id(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                TeamDTO.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().name()).isEqualTo(updatedName);
        assertThat(updated.getBody().shortName()).isEqualTo("NNF");
    }
}
