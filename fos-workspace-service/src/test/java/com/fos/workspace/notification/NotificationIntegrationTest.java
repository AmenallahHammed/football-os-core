package com.fos.workspace.notification;

import com.fos.sdk.test.FosTestContainersBase;
import com.fos.workspace.notification.api.NotificationResponse;
import com.fos.workspace.notification.application.NotificationService;
import com.fos.workspace.notification.domain.NotificationType;
import com.fos.workspace.notification.domain.WorkspaceNotification;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "fos.storage.provider=noop",
    "spring.security.enabled=false"
})
class NotificationIntegrationTest extends FosTestContainersBase {

    @Autowired
    private WorkspaceNotificationRepository notificationRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final UUID TEST_ACTOR_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    @Test
    void should_return_notifications_for_actor() {
        // Directly insert a notification for test actor
        WorkspaceNotification n = WorkspaceNotification.create(
                TEST_ACTOR_ID, null,
                NotificationType.DOCUMENT_MISSING,
                "Test Notification", "Test body",
                null, null);
        notificationRepository.save(n);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/notifications", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return_unread_count() {
        // Insert 3 unread notifications
        for (int i = 0; i < 3; i++) {
            WorkspaceNotification n = WorkspaceNotification.create(
                    TEST_ACTOR_ID, null, NotificationType.GENERAL,
                    "Notification " + i, "Body", null, null);
            notificationRepository.save(n);
        }

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/notifications/unread-count", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("count");
    }

    @Test
    void should_mark_notification_as_read() {
        WorkspaceNotification n = WorkspaceNotification.create(
                TEST_ACTOR_ID, null, NotificationType.GENERAL,
                "Mark Read Test", "Body", null, null);
        WorkspaceNotification saved = notificationRepository.save(n);

        String endpoint = restTemplate.getRootUri()
                + "/api/v1/notifications/" + saved.getResourceId() + "/read";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        WorkspaceNotification updated = notificationRepository
                .findByResourceId(saved.getResourceId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
    }
}
