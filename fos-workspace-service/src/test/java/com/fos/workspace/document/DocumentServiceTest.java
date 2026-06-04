package com.fos.workspace.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.api.InitiateUploadRequest;
import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
                .thenThrow(new IllegalStateException("MinIO upload URL generation failed"));

        assertThatThrownBy(() -> documentService.initiateUpload(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MinIO upload URL generation failed");

        verify(documentRepository).deleteById(anyString());
        verify(kafkaProducer, never()).emit(any());
    }
}
