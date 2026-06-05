package com.fos.workspace.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.PresignedUploadUrl;
import com.fos.sdk.storage.StoragePort;
import com.fos.sdk.storage.StorageOperationException;
import com.fos.workspace.document.api.InitiateUploadRequest;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @Test
    void should_delete_saved_draft_when_upload_url_generation_fails() {
        WorkspaceDocumentRepository documentRepository = mock(WorkspaceDocumentRepository.class);
        StoragePort storagePort = mock(StoragePort.class);
        PolicyClient policyClient = mock(PolicyClient.class);
        FosKafkaProducer kafkaProducer = mock(FosKafkaProducer.class);
        FosSecurityContext securityContext = mock(FosSecurityContext.class);
        DocumentService documentService = new DocumentService(
                documentRepository,
                storagePort,
                policyClient,
                kafkaProducer,
                securityContext,
                new ObjectMapper(),
                "fos-workspace",
                true);

        UUID actorId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        InitiateUploadRequest request = new InitiateUploadRequest(
                "Match plan",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "match-plan.pdf",
                "application/pdf",
                2048L,
                null,
                null,
                List.of("plan"),
                "Uploaded from test");

        when(securityContext.getActorId()).thenReturn(actorId);
        when(securityContext.clubId()).thenReturn(clubId.toString());
        when(securityContext.getRole()).thenReturn("ROLE_CLUB_ADMIN");
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.allow());
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storagePort.generateUploadUrl(eq("fos-workspace"), anyString(), eq("application/pdf"), any()))
                .thenThrow(new StorageOperationException("MinIO upload URL generation failed for objectKey=documents/test.pdf"));

        assertThatThrownBy(() -> documentService.initiateUpload(request))
                .isInstanceOf(StorageOperationException.class)
                .hasMessage("MinIO upload URL generation failed for objectKey=documents/test.pdf");

        verify(documentRepository).deleteById(anyString());
        verify(kafkaProducer, never()).emit(any());
    }

    @Test
    void should_generate_storage_safe_object_key_for_special_characters() {
        WorkspaceDocumentRepository documentRepository = mock(WorkspaceDocumentRepository.class);
        StoragePort storagePort = mock(StoragePort.class);
        PolicyClient policyClient = mock(PolicyClient.class);
        FosKafkaProducer kafkaProducer = mock(FosKafkaProducer.class);
        FosSecurityContext securityContext = mock(FosSecurityContext.class);
        DocumentService documentService = new DocumentService(
                documentRepository,
                storagePort,
                policyClient,
                kafkaProducer,
                securityContext,
                new ObjectMapper(),
                "fos-workspace",
                true);

        UUID actorId = UUID.randomUUID();
        UUID clubId = UUID.randomUUID();
        InitiateUploadRequest request = new InitiateUploadRequest(
                "Seance d'entrainement",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                "SEANCE D'ENTRAINEMENT MARDI LE 19 MAI 2026 NUM 212.pdf",
                "application/pdf",
                2048L,
                null,
                null,
                List.of("plan"),
                "Uploaded from test");

        when(securityContext.getActorId()).thenReturn(actorId);
        when(securityContext.clubId()).thenReturn(clubId.toString());
        when(securityContext.getRole()).thenReturn("ROLE_CLUB_ADMIN");
        when(policyClient.evaluate(any())).thenReturn(PolicyResult.allow());
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storagePort.generateUploadUrl(eq("fos-workspace"), anyString(), eq("application/pdf"), any()))
                .thenAnswer(invocation -> new PresignedUploadUrl(
                        "https://noop.fos.local/upload/" + invocation.getArgument(1, String.class),
                        invocation.getArgument(1, String.class),
                        Instant.now()));

        DocumentService.UploadInitiationResult result = documentService.initiateUpload(request);
        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);

        verify(storagePort).generateUploadUrl(eq("fos-workspace"), objectKeyCaptor.capture(), eq("application/pdf"), any());

        assertThat(result.objectKey()).isEqualTo(objectKeyCaptor.getValue());
        assertThat(result.objectKey()).startsWith("documents/" + clubId + "/");
        assertThat(result.objectKey()).contains("/seance-d-entrainement-mardi-le-19-mai-2026-num-212-v1-");
        assertThat(result.objectKey()).endsWith(".pdf");
        assertThat(result.objectKey()).doesNotContain(" ", "'", "(", ")", "#", "\u00E9", "\\");
    }
}
