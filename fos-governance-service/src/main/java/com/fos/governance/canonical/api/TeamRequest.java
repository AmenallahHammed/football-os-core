package com.fos.governance.canonical.api;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record TeamRequest(
    @NotBlank String name,
    String shortName,
    String country,
    UUID clubId
) {}
