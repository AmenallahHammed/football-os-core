package com.fos.workspace.document.api;

import com.fos.workspace.document.domain.DocumentCategory;
import com.fos.workspace.document.domain.DocumentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record InitiateUploadRequest(
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
