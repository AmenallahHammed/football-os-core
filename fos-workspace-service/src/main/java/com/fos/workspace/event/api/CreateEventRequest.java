package com.fos.workspace.event.api;

import com.fos.workspace.event.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank String title,
        String description,
        @NotNull EventType type,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        String location,
        UUID teamRefId,
        List<AttendeeInput> attendees,
        List<RequiredDocumentInput> requiredDocuments,
        List<TaskInput> tasks
) {
    public record AttendeeInput(UUID actorId, boolean mandatory, String canonicalType) {
    }

    public record RequiredDocumentInput(String description, String documentCategory, UUID assignedToActorId) {
    }

    public record TaskInput(String title, String description, UUID assignedToActorId, Instant dueAt) {
    }
}
