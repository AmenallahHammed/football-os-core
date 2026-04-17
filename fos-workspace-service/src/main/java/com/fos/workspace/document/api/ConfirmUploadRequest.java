package com.fos.workspace.document.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConfirmUploadRequest(
        @NotNull UUID documentId,
        @NotBlank String storageObjectKey,
        @NotBlank String storageBucket
) {
}
