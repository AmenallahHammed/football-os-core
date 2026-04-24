package com.fos.workspace.onlyoffice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.onlyoffice.application.OnlyOfficeSaveHandler;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OnlyOfficeSaveHandlerTest {

    private WorkspaceDocumentRepository documentRepository;
    private StoragePort storagePort;
    private FosKafkaProducer kafkaProducer;
    private OnlyOfficeSaveHandler saveHandler;
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        documentRepository = mock(WorkspaceDocumentRepository.class);
        storagePort = mock(StoragePort.class);
        kafkaProducer = mock(FosKafkaProducer.class);
        saveHandler = new OnlyOfficeSaveHandler(documentRepository, storagePort, kafkaProducer, new ObjectMapper());

        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void should_upload_and_append_new_version_after_successful_onlyoffice_save() {
        WorkspaceDocument document = documentWithInitialVersion();
        UUID documentId = document.getResourceId();
        byte[] updatedBytes = "updated-document-content".getBytes(StandardCharsets.UTF_8);

        wireMock.stubFor(get(urlEqualTo("/download/doc"))
                .willReturn(aResponse().withStatus(200).withBody(updatedBytes)));

        when(documentRepository.findByResourceId(documentId)).thenReturn(Optional.of(document));

        String callbackBody = "{\"status\":2,\"url\":\"http://localhost:" + wireMock.port() + "/download/doc\"}";
        saveHandler.handleCallback(documentId, callbackBody);

        String expectedKey = "documents/" + documentId + "/v2_test.docx";
        verify(storagePort).putObject(
                eq("fos-workspace"),
                eq(expectedKey),
                any(InputStream.class),
                eq((long) updatedBytes.length),
                eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        verify(storagePort).confirmUpload("fos-workspace", expectedKey);
        verify(documentRepository).save(document);
        verify(kafkaProducer).emit(any());

        assertThat(document.currentVersion().getVersionNumber()).isEqualTo(2);
        assertThat(document.currentVersion().getStorageObjectKey()).isEqualTo(expectedKey);
        assertThat(document.currentVersion().getFileSizeBytes()).isEqualTo((long) updatedBytes.length);
    }

    @Test
    void should_not_append_new_version_when_storage_upload_fails() {
        WorkspaceDocument document = documentWithInitialVersion();
        UUID documentId = document.getResourceId();
        byte[] updatedBytes = "updated-document-content".getBytes(StandardCharsets.UTF_8);

        wireMock.stubFor(get(urlEqualTo("/download/doc"))
                .willReturn(aResponse().withStatus(200).withBody(updatedBytes)));

        when(documentRepository.findByResourceId(documentId)).thenReturn(Optional.of(document));
        doThrow(new IllegalStateException("upload failed"))
                .when(storagePort)
                .putObject(anyString(), anyString(), any(InputStream.class), anyLong(), anyString());

        String callbackBody = "{\"status\":2,\"url\":\"http://localhost:" + wireMock.port() + "/download/doc\"}";
        saveHandler.handleCallback(documentId, callbackBody);

        verify(documentRepository, never()).save(any());
        verify(storagePort, never()).confirmUpload(anyString(), anyString());
        verify(kafkaProducer, never()).emit(any());
        assertThat(document.currentVersion().getVersionNumber()).isEqualTo(1);
    }

    @Test
    void should_ignore_non_save_status_callback() {
        UUID documentId = UUID.randomUUID();

        saveHandler.handleCallback(documentId, "{\"status\":1}");

        verifyNoInteractions(documentRepository, storagePort, kafkaProducer);
    }

    private WorkspaceDocument documentWithInitialVersion() {
        UUID clubId = UUID.randomUUID();
        WorkspaceDocument document = WorkspaceDocument.create(
                "test.docx",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                CanonicalRef.club(clubId),
                null,
                null,
                List.of());

        String initialKey = "documents/" + document.getResourceId() + "/v1_test.docx";
        document.addVersion(new DocumentVersion(
                initialKey,
                "fos-workspace",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                100L,
                1,
                UUID.randomUUID(),
                "Initial upload"));

        return document;
    }
}
