package com.fos.workspace.event;

import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.event.api.CreateEventRequest;
import com.fos.workspace.event.api.EventResponse;
import com.fos.workspace.event.api.UpdateEventRequest;
import com.fos.workspace.event.domain.EventType;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "fos.storage.provider=noop",
        "fos.security.enabled=false"
})
class EventIntegrationTest extends FosTestContainersBase {

    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12")).withReuse(true);

    private static final boolean USING_EXTERNAL_MONGO;

    static {
        boolean started = false;
        try {
            MONGO.start();
            started = true;
        } catch (Throwable ex) {
            System.err.println("[EventIntegrationTest] Mongo container unavailable, falling back to external MongoDB: " + ex.getMessage());
        }
        USING_EXTERNAL_MONGO = !started;
    }

    static WireMockServer wireMock;

    private static WireMockServer wireMock() {
        if (wireMock == null) {
            wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            wireMock.start();
        }
        return wireMock;
    }

    @BeforeAll
    static void startWireMock() {
        wireMock();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("fos.policy.service-url", () -> "http://localhost:" + wireMock().port());
        registry.add("spring.data.mongodb.uri", () -> USING_EXTERNAL_MONGO
                ? Optional.ofNullable(System.getenv("MONGODB_URI")).orElse("mongodb://localhost:27017/fos_workspace")
                : MONGO.getReplicaSetUrl("fos_workspace"));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkspaceEventRepository repository;

    @MockBean
    private FosKafkaProducer kafkaProducer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        wireMock().resetAll();
        wireMock().stubFor(post(urlEqualTo("/api/v1/policy/evaluate"))
                .willReturn(okJson("{\"decision\":\"ALLOW\",\"reason\":\"allowed\"}")));
    }

    @Test
    void should_create_event_and_return_201() {
        CreateEventRequest request = new CreateEventRequest(
                "Pre-Season Training",
                "First training of the season",
                EventType.TRAINING,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                "Training Ground A",
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of());

        ResponseEntity<EventResponse> response = restTemplate.postForEntity("/api/v1/events", request, EventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("Pre-Season Training");
        assertThat(response.getBody().type()).isEqualTo(EventType.TRAINING);
        assertThat(response.getBody().state().name()).isEqualTo("ACTIVE");
    }

    @Test
    void should_update_event_title() {
        CreateEventRequest createRequest = new CreateEventRequest(
                "Original Title",
                null,
                EventType.MEETING,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of());

        EventResponse created = restTemplate.postForObject("/api/v1/events", createRequest, EventResponse.class);
        UpdateEventRequest updateRequest = new UpdateEventRequest("Updated Title", null, null, null, null);

        restTemplate.put("/api/v1/events/" + created.eventId(), updateRequest);

        EventResponse updated = restTemplate.getForObject("/api/v1/events/" + created.eventId(), EventResponse.class);
        assertThat(updated.title()).isEqualTo("Updated Title");
    }

    @Test
    void should_delete_event_and_return_204() {
        CreateEventRequest createRequest = new CreateEventRequest(
                "To Delete",
                null,
                EventType.OTHER,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                UUID.randomUUID(),
                List.of(),
                List.of(),
                List.of());

        EventResponse created = restTemplate.postForObject("/api/v1/events", createRequest, EventResponse.class);

        restTemplate.delete("/api/v1/events/" + created.eventId());

        EventResponse deleted = restTemplate.getForObject("/api/v1/events/" + created.eventId(), EventResponse.class);
        assertThat(deleted.state().name()).isEqualTo("ARCHIVED");
    }

    @Test
    void should_list_events_by_team() {
        UUID teamId = UUID.randomUUID();

        for (int i = 1; i <= 2; i++) {
            CreateEventRequest request = new CreateEventRequest(
                    "Event " + i,
                    null,
                    EventType.TRAINING,
                    Instant.now().plus(i, ChronoUnit.DAYS),
                    Instant.now().plus(i, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                    null,
                    teamId,
                    List.of(),
                    List.of(),
                    List.of());
            restTemplate.postForObject("/api/v1/events", request, EventResponse.class);
        }

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/events?teamRefId=" + teamId, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
