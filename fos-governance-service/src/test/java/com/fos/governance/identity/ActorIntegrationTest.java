package com.fos.governance.identity;

import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.api.ActorResponse;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.sdk.core.ResourceState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.fos.sdk.test.FosTestContainersBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActorIntegrationTest extends FosTestContainersBase {

    private static String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@testclub.com";
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_create_actor_and_return_201() {
        String email = uniqueEmail("player");
        ActorRequest request = new ActorRequest(
                email, "Carlos", "Silva",
                ActorRole.PLAYER, UUID.randomUUID());

        ResponseEntity<ActorResponse> response = restTemplate.postForEntity(
                "/api/v1/actors", request, ActorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo(email);
        assertThat(response.getBody().role()).isEqualTo(ActorRole.PLAYER);
        assertThat(response.getBody().state()).isEqualTo(ResourceState.DRAFT);
    }

    @Test
    void should_return_actor_by_id() {
        String email = uniqueEmail("coach");
        ActorRequest request = new ActorRequest(
                email, "Marco", "Rossi",
                ActorRole.HEAD_COACH, UUID.randomUUID());

        ActorResponse created = restTemplate.postForObject(
                "/api/v1/actors", request, ActorResponse.class);

        ResponseEntity<ActorResponse> fetched = restTemplate.getForEntity(
                "/api/v1/actors/" + created.resourceId(), ActorResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().resourceId()).isEqualTo(created.resourceId());
    }

    @Test
    void should_deactivate_actor() {
        String email = uniqueEmail("admin");
        ActorRequest request = new ActorRequest(
                email, "Anna", "Klein",
                ActorRole.CLUB_ADMIN, UUID.randomUUID());

        ActorResponse created = restTemplate.postForObject(
                "/api/v1/actors", request, ActorResponse.class);

        restTemplate.delete("/api/v1/actors/" + created.resourceId());

        ActorResponse fetched = restTemplate.getForObject(
                "/api/v1/actors/" + created.resourceId(), ActorResponse.class);

        assertThat(fetched.state()).isEqualTo(ResourceState.ARCHIVED);
    }

    @Test
    void should_update_actor() {
        String createEmail = uniqueEmail("update");
        String updateEmail = uniqueEmail("updated");
        ActorRequest createRequest = new ActorRequest(
                createEmail, "Old", "Name",
                ActorRole.PLAYER, UUID.randomUUID());

        ActorResponse created = restTemplate.postForObject(
                "/api/v1/actors", createRequest, ActorResponse.class);

        ActorRequest updateRequest = new ActorRequest(
                updateEmail, "New", "Name",
                ActorRole.HEAD_COACH, created.clubId());

        ResponseEntity<ActorResponse> updated = restTemplate.exchange(
                "/api/v1/actors/" + created.resourceId(),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                ActorResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().email()).isEqualTo(updateEmail);
        assertThat(updated.getBody().firstName()).isEqualTo("New");
        assertThat(updated.getBody().role()).isEqualTo(ActorRole.HEAD_COACH);
    }
}
