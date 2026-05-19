package com.fos.workspace.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.workspace.notification.domain.WorkspaceNotification;
import com.fos.workspace.notification.infrastructure.persistence.WorkspaceNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceKafkaConsumerTest {

    private WorkspaceNotificationRepository notificationRepository;
    private WorkspaceKafkaConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(WorkspaceNotificationRepository.class);
        objectMapper = new ObjectMapper();
        consumer = new WorkspaceKafkaConsumer(objectMapper, notificationRepository);
        when(notificationRepository.save(any(WorkspaceNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void should_use_uploader_actor_id_from_payload_before_actor_ref() {
        UUID uploaderId = UUID.randomUUID();
        UUID legacyActorRefId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode()
                .put("uploaderActorId", uploaderId.toString());

        consumer.handle(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("fos.workspace.document.uploaded")
                .actorRef("CanonicalRef[type=CLUB, id=" + legacyActorRefId + "]")
                .payload(payload)
                .build());

        ArgumentCaptor<WorkspaceNotification> captor = ArgumentCaptor.forClass(WorkspaceNotification.class);
        verify(notificationRepository).save(captor.capture());
        WorkspaceNotification saved = captor.getValue();
        assertThat(saved.getRecipientActorId()).isEqualTo(uploaderId);
        assertThat(saved.getTriggeredByActorId()).isEqualTo(uploaderId);
    }

    @Test
    void should_fallback_to_legacy_actor_ref_when_uploader_actor_id_is_missing() {
        UUID legacyActorRefId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode();

        consumer.handle(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic("fos.workspace.document.uploaded")
                .actorRef("CanonicalRef[type=CLUB, id=" + legacyActorRefId + "]")
                .payload(payload)
                .build());

        ArgumentCaptor<WorkspaceNotification> captor = ArgumentCaptor.forClass(WorkspaceNotification.class);
        verify(notificationRepository).save(captor.capture());
        WorkspaceNotification saved = captor.getValue();
        assertThat(saved.getRecipientActorId()).isEqualTo(legacyActorRefId);
        assertThat(saved.getTriggeredByActorId()).isEqualTo(legacyActorRefId);
    }
}
