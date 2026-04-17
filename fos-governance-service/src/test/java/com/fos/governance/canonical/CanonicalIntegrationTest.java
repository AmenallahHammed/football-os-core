package com.fos.governance.canonical;

import com.fos.governance.canonical.api.PlayerRequest;
import com.fos.governance.canonical.api.TeamRequest;
import com.fos.sdk.canonical.PlayerDTO;
import com.fos.sdk.canonical.TeamDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CanonicalIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("fos_governance");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_create_player_and_return_201() {
        PlayerRequest request = new PlayerRequest(
                "Lionel Test", "CF", "AR", LocalDate.of(1987, 6, 24), null);

        ResponseEntity<PlayerDTO> response = restTemplate.postForEntity(
                "/api/v1/players", request, PlayerDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Lionel Test");
        assertThat(response.getBody().position()).isEqualTo("CF");
    }

    @Test
    void should_find_player_by_identity_for_dedup() {
        PlayerRequest request = new PlayerRequest(
                "Dedup Player", "CM", "BR", LocalDate.of(1995, 3, 15), null);

        // Create player
        restTemplate.postForObject("/api/v1/players", request, PlayerDTO.class);

        // Find by identity (dedup endpoint)
        ResponseEntity<PlayerDTO> found = restTemplate.getForEntity(
                "/api/v1/players/find?name=Dedup+Player&dob=1995-03-15&nationality=BR",
                PlayerDTO.class);

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().name()).isEqualTo("Dedup Player");
    }

    @Test
    void should_not_find_nonexistent_player_by_identity() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/players/find?name=Ghost+Player&dob=2000-01-01&nationality=XX",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_create_team_and_return_201() {
        TeamRequest request = new TeamRequest("Test FC", "TFC", "ES", null);

        ResponseEntity<TeamDTO> response = restTemplate.postForEntity(
                "/api/v1/teams", request, TeamDTO.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Test FC");
    }
}
