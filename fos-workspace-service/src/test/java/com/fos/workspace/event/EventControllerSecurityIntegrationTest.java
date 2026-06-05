package com.fos.workspace.event;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.workspace.event.domain.EventType;
import com.fos.workspace.event.domain.WorkspaceEvent;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "fos.storage.provider=noop",
        "spring.kafka.listener.auto-startup=false"
})
class EventControllerSecurityIntegrationTest {

    private static final UUID CLUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("f8cbadea-8eda-46b5-9117-d00ce9148aa2");

    private static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0.12")).withReuse(true);

    private static final boolean USING_EXTERNAL_MONGO;

    static {
        boolean started = false;
        try {
            MONGO.start();
            started = true;
        } catch (Throwable ex) {
            System.err.println("[EventControllerSecurityIntegrationTest] Mongo container unavailable, falling back to external MongoDB: "
                    + ex.getMessage());
        }
        USING_EXTERNAL_MONGO = !started;
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> USING_EXTERNAL_MONGO
                ? Optional.ofNullable(System.getenv("MONGODB_URI")).orElse("mongodb://localhost:27017/fos_workspace")
                : MONGO.getReplicaSetUrl("fos_workspace"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkspaceEventRepository repository;

    @MockBean
    private PolicyClient policyClient;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.allow());
    }

    @Test
    void should_list_events_for_unprefixed_head_coach_role_claim() throws Exception {
        repository.save(WorkspaceEvent.create(
                "Video Review",
                "Review tactical clips",
                EventType.MEETING,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(90, ChronoUnit.MINUTES),
                "Meeting Room",
                CanonicalRef.club(CLUB_ID),
                CanonicalRef.team(TEAM_ID)));

        mockMvc.perform(get("/api/v1/events")
                        .with(jwt().jwt(token -> token
                                .subject(ACTOR_ID.toString())
                                .claim("fos_club_id", CLUB_ID.toString())
                                .claim("realm_access", Map.of("roles", List.of("HEAD_COACH")))))
                        .param("teamRefId", TEAM_ID.toString())
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        ArgumentCaptor<PolicyRequest> policyCaptor = ArgumentCaptor.forClass(PolicyRequest.class);
        verify(policyClient).evaluate(policyCaptor.capture());
        assertThat(policyCaptor.getValue().actorRole()).isEqualTo("ROLE_HEAD_COACH");
    }

    @Test
    void should_return_empty_page_when_team_has_no_events() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                        .with(jwt().jwt(token -> token
                                .subject(ACTOR_ID.toString())
                                .claim("fos_club_id", CLUB_ID.toString())
                                .claim("realm_access", Map.of("roles", List.of("HEAD_COACH")))))
                        .param("teamRefId", TEAM_ID.toString())
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
