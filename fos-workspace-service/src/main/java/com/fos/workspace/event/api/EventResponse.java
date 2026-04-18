package com.fos.workspace.event.api;

import com.fos.sdk.core.ResourceState;
import com.fos.workspace.event.domain.AttendeeRef;
import com.fos.workspace.event.domain.EventType;
import com.fos.workspace.event.domain.RequiredDocument;
import com.fos.workspace.event.domain.TaskAssignment;
import com.fos.workspace.event.domain.WorkspaceEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID eventId,
        String title,
        String description,
        EventType type,
        Instant startAt,
        Instant endAt,
        String location,
        UUID createdByActorId,
        UUID teamRefId,
        ResourceState state,
        List<AttendeeRef> attendees,
        List<RequiredDocument> requiredDocuments,
        List<TaskAssignment> tasks,
        boolean reminderSent,
        Instant createdAt
) {
    public static EventResponse from(WorkspaceEvent event) {
        return new EventResponse(
                event.getResourceId(),
                event.getTitle(),
                event.getDescription(),
                event.getType(),
                event.getStartAt(),
                event.getEndAt(),
                event.getLocation(),
                event.getCreatedByRef() != null ? event.getCreatedByRef().id() : null,
                event.getTeamRef() != null ? event.getTeamRef().id() : null,
                event.getState(),
                event.getAttendees(),
                event.getRequiredDocuments(),
                event.getTasks(),
                event.isReminderSent(),
                event.getCreatedAt());
    }
}
