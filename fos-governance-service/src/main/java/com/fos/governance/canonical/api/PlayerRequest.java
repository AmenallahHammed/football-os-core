package com.fos.governance.canonical.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

public record PlayerRequest(
    @NotBlank String name,
    String position,
    String nationality,
    LocalDate dateOfBirth,
    UUID currentTeamId
) {}
