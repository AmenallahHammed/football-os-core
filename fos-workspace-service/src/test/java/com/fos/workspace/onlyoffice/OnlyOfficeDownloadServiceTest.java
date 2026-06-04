package com.fos.workspace.onlyoffice;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.storage.StoragePort;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.domain.WorkspaceDocument;
import com.fos.workspace.document.infrastructure.persistence.WorkspaceDocumentRepository;
import com.fos.workspace.onlyoffice.application.OnlyOfficeDownloadService;
import com.fos.workspace.onlyoffice.application.OnlyOfficeDownloadTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlyOfficeDownloadServiceTest {

    private static final String JWT_SECRET = "test-secret-key-must-be-32-chars!!";

    private WorkspaceDocumentRepository documentRepository;
    private StoragePort storagePort;
    private OnlyOfficeDownloadTokenService tokenService;
    private OnlyOfficeDownloadService downloadService;

    @BeforeEach
    void setUp() {
        documentRepository = mock(WorkspaceDocumentRepository.class);
        storagePort = mock(StoragePort.class);
        tokenService = new OnlyOfficeDownloadTokenService(JWT_SECRET, 60);
        downloadService = new OnlyOfficeDownloadService(documentRepository, storagePort, tokenService);
    }

    @Test
    void should_download_document_bytes_for_valid_onlyoffice_token() {
        WorkspaceDocument document = documentWithVersions();
        DocumentVersion version = document.getVersions().get(0);
        String documentKey = document.getResourceId() + "_v1";
        String token = tokenService.signDownloadToken(document.getResourceId(), 1);
        byte[] fileBytes = "docx-binary".getBytes();

        when(documentRepository.findByResourceId(document.getResourceId())).thenReturn(Optional.of(document));
        when(storagePort.getObject(version.getStorageBucket(), version.getStorageObjectKey())).thenReturn(fileBytes);

        OnlyOfficeDownloadService.DownloadedDocument downloaded = downloadService.downloadDocument(documentKey, token);

        assertThat(downloaded.originalFilename()).isEqualTo("test.docx");
        assertThat(downloaded.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(downloaded.fileSizeBytes()).isEqualTo(123L);
        assertThat(downloaded.bytes()).isEqualTo(fileBytes);
        verify(storagePort).getObject(version.getStorageBucket(), version.getStorageObjectKey());
    }

    @Test
    void should_reject_mismatched_onlyoffice_download_token() {
        WorkspaceDocument document = documentWithVersions();
        String documentKey = document.getResourceId() + "_v1";
        String token = tokenService.signDownloadToken(document.getResourceId(), 2);

        assertThatThrownBy(() -> downloadService.downloadDocument(documentKey, token))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void should_reject_missing_onlyoffice_download_token() {
        WorkspaceDocument document = documentWithVersions();
        String documentKey = document.getResourceId() + "_v1";

        assertThatThrownBy(() -> downloadService.downloadDocument(documentKey, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void should_reject_expired_onlyoffice_download_token() {
        WorkspaceDocument document = documentWithVersions();
        String documentKey = document.getResourceId() + "_v1";
        String token = Jwts.builder()
                .claim("purpose", "onlyoffice-download")
                .claim("documentId", document.getResourceId().toString())
                .claim("version", 1)
                .issuedAt(java.util.Date.from(Instant.now().minusSeconds(120)))
                .expiration(java.util.Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> downloadService.downloadDocument(documentKey, token))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void should_reject_wrong_purpose_onlyoffice_download_token() {
        WorkspaceDocument document = documentWithVersions();
        String documentKey = document.getResourceId() + "_v1";
        String token = Jwts.builder()
                .claim("purpose", "onlyoffice-callback")
                .claim("documentId", document.getResourceId().toString())
                .claim("version", 1)
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> downloadService.downloadDocument(documentKey, token))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void should_reject_malformed_document_key_with_unauthorized() {
        WorkspaceDocument document = documentWithVersions();
        String token = tokenService.signDownloadToken(document.getResourceId(), 1);

        assertThatThrownBy(() -> downloadService.downloadDocument(document.getResourceId().toString(), token))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void should_fail_when_requested_document_version_is_missing() {
        WorkspaceDocument document = documentWithVersions();
        String documentKey = document.getResourceId() + "_v3";
        String token = tokenService.signDownloadToken(document.getResourceId(), 3);

        when(documentRepository.findByResourceId(document.getResourceId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> downloadService.downloadDocument(documentKey, token))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Document version not found");
    }

    private WorkspaceDocument documentWithVersions() {
        UUID clubId = UUID.randomUUID();
        WorkspaceDocument document = WorkspaceDocument.create(
                "Test document",
                null,
                DocumentCategory.GENERAL,
                DocumentVisibility.CLUB_WIDE,
                CanonicalRef.club(clubId),
                null,
                null,
                List.of());

        document.addVersion(new DocumentVersion(
                "documents/" + document.getResourceId() + "/v1_test.docx",
                "fos-workspace",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                123L,
                1,
                UUID.randomUUID(),
                "Uploaded"));

        document.addVersion(new DocumentVersion(
                "documents/" + document.getResourceId() + "/v2_test.docx",
                "fos-workspace",
                "test.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                456L,
                2,
                UUID.randomUUID(),
                "Updated"));

        return document;
    }
}
