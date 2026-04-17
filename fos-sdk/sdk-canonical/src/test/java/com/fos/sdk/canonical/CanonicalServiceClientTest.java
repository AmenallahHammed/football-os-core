package com.fos.sdk.canonical;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest
@org.springframework.context.annotation.Import(CanonicalServiceClient.class)
class CanonicalServiceClientTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class TestConfig {}

    @Autowired
    private CanonicalServiceClient client;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void should_return_player_when_found_by_id() {
        UUID playerId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8081/api/v1/players/" + playerId))
              .andRespond(withSuccess("""
                  {"id":"%s","name":"Lionel Test","position":"CF",
                   "nationality":"AR","dateOfBirth":"1990-01-01","currentTeamId":null}
                  """.formatted(playerId), MediaType.APPLICATION_JSON));

        PlayerDTO result = client.getPlayer(playerId);

        assertThat(result.id()).isEqualTo(playerId);
        assertThat(result.name()).isEqualTo("Lionel Test");
    }

    @Test
    void should_return_team_when_found_by_id() {
        UUID teamId = UUID.randomUUID();
        server.expect(requestTo("http://localhost:8081/api/v1/teams/" + teamId))
              .andRespond(withSuccess("""
                  {"id":"%s","name":"Test FC","shortName":"TFC",
                   "country":"ES","clubId":null}
                  """.formatted(teamId), MediaType.APPLICATION_JSON));

        TeamDTO result = client.getTeam(teamId);

        assertThat(result.id()).isEqualTo(teamId);
        assertThat(result.name()).isEqualTo("Test FC");
    }
}
