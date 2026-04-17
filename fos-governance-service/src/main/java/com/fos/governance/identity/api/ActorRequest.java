package com.fos.governance.identity.api;

import com.fos.governance.identity.domain.ActorRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActorRequest(
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull ActorRole role,
    UUID clubId
) {}
