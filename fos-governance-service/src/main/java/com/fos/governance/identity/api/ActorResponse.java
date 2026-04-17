package com.fos.governance.identity.api;

import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.sdk.core.ResourceState;

import java.time.Instant;
import java.util.UUID;

public record ActorResponse(
    UUID resourceId,
    String email,
    String firstName,
    String lastName,
    ActorRole role,
    ResourceState state,
    UUID clubId,
    Instant createdAt
) {
    public static ActorResponse from(Actor a) {
        return new ActorResponse(
            a.getResourceId(), a.getEmail(), a.getFirstName(), a.getLastName(),
            a.getRole(), a.getState(), a.getClubId(), a.getCreatedAt()
        );
    }
}
