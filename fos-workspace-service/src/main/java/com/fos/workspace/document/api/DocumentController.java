package com.fos.workspace.document.api;

import com.fos.workspace.document.application.DocumentService;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload/initiate")
    public ResponseEntity<DocumentService.UploadInitiationResult> initiateUpload(@Valid @RequestBody InitiateUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.initiateUpload(request));
    }

    @PostMapping("/upload/confirm")
    public DocumentResponse confirmUpload(@Valid @RequestBody ConfirmUploadWithMetadata request) {
        return documentService.confirmUpload(
                new ConfirmUploadRequest(request.documentId(), request.storageObjectKey(), request.storageBucket()),
                new InitiateUploadRequest(
                        request.name(),
                        request.description(),
                        request.category(),
                        request.visibility(),
                        request.originalFilename(),
                        request.contentType(),
                        request.fileSizeBytes(),
                        request.linkedPlayerRefId(),
                        request.linkedTeamRefId(),
                        request.tags(),
                        request.versionNote()));
    }

    @GetMapping("/{documentId}")
    public DocumentResponse getDocument(@PathVariable UUID documentId) {
        return documentService.getDocument(documentId);
    }

    @GetMapping
    public Page<DocumentResponse> listDocuments(@RequestParam DocumentCategory category,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return documentService.listDocuments(category, pageable);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDeleteDocument(@PathVariable UUID documentId) {
        documentService.softDeleteDocument(documentId);
    }

    public record ConfirmUploadWithMetadata(
            @NotNull UUID documentId,
            @NotBlank String storageObjectKey,
            @NotBlank String storageBucket,
            @NotBlank String name,
            String description,
            @NotNull DocumentCategory category,
            @NotNull DocumentVisibility visibility,
            @NotBlank String originalFilename,
            @NotBlank String contentType,
            @NotNull Long fileSizeBytes,
            UUID linkedPlayerRefId,
            UUID linkedTeamRefId,
            List<String> tags,
            String versionNote
    ) {
    }
}
