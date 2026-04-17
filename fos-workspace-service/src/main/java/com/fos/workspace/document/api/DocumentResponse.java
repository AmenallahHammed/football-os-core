package com.fos.workspace.document.api;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVersion;
import com.fos.workspace.document.domain.DocumentVisibility;
import com.fos.workspace.document.domain.WorkspaceDocument;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        UUID documentId,
        String name,
        String description,
        DocumentCategory category,
        DocumentVisibility visibility,
        ResourceState state,
        UUID ownerRefId,
        UUID linkedPlayerRefId,
        UUID linkedTeamRefId,
        List<String> tags,
        int versionCount,
        CurrentVersionInfo currentVersion,
        Instant createdAt,
        Instant updatedAt,
        String downloadUrl
) {

    public static DocumentResponse from(WorkspaceDocument doc, String downloadUrl) {
        DocumentVersion current = doc.currentVersion();
        CurrentVersionInfo versionInfo = current == null ? null : new CurrentVersionInfo(
                current.getVersionId(),
                current.getOriginalFilename(),
                current.getContentType(),
                current.getFileSizeBytes(),
                current.getVersionNumber(),
                current.getUploadedByActorId(),
                current.getUploadedAt(),
                current.getVersionNote()
        );

        return new DocumentResponse(
                doc.getResourceId(),
                doc.getName(),
                doc.getDescription(),
                doc.getCategory(),
                doc.getVisibility(),
                doc.getState(),
                doc.getOwnerRef() != null ? doc.getOwnerRef().id() : null,
                doc.getLinkedPlayerRef() != null ? doc.getLinkedPlayerRef().id() : null,
                doc.getLinkedTeamRef() != null ? doc.getLinkedTeamRef().id() : null,
                doc.getTags(),
                doc.getVersions().size(),
                versionInfo,
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                downloadUrl
        );
    }

    public record CurrentVersionInfo(
            UUID versionId,
            String originalFilename,
            String contentType,
            Long fileSizeBytes,
            int versionNumber,
            UUID uploadedByActorId,
            Instant uploadedAt,
            String versionNote
    ) {
    }
}
