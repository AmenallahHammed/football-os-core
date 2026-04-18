package com.fos.workspace.onlyoffice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OnlyOfficeConfigRequest(
        @NotNull UUID documentId,
        @NotBlank String mode
) {
}
