package com.fos.workspace.event.domain;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.core.BaseDocument;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A calendar event in the workspace with embedded attendees, requirements, and tasks.
 */
@Document(collection = "workspace_events")
public class WorkspaceEvent extends BaseDocument {

    private String title;
    private String description;
    private EventType type;
    private Instant startAt;
    private Instant endAt;
    private String location;
    private CanonicalRef createdByRef;
    private CanonicalRef teamRef;
    private List<AttendeeRef> attendees = new ArrayList<>();
    private List<RequiredDocument> requiredDocuments = new ArrayList<>();
    private List<TaskAssignment> tasks = new ArrayList<>();
    private boolean reminderSent;

    protected WorkspaceEvent() {
    }

    public static WorkspaceEvent create(String title,
                                        String description,
                                        EventType type,
                                        Instant startAt,
                                        Instant endAt,
                                        String location,
                                        CanonicalRef createdByRef,
                                        CanonicalRef teamRef) {
        validateTimeRange(startAt, endAt);

        WorkspaceEvent event = new WorkspaceEvent();
        event.initId();
        event.title = title;
        event.description = description;
        event.type = type;
        event.startAt = startAt;
        event.endAt = endAt;
        event.location = location;
        event.createdByRef = createdByRef;
        event.teamRef = teamRef;
        event.activate();
        return event;
    }

    public void addAttendee(AttendeeRef attendee) {
        this.attendees.add(attendee);
    }

    public void addRequiredDocument(RequiredDocument requiredDoc) {
        this.requiredDocuments.add(requiredDoc);
    }

    public void addTask(TaskAssignment task) {
        if (task.getDueAt() != null && endAt != null && task.getDueAt().isAfter(endAt)) {
            throw new IllegalArgumentException("Task dueAt cannot be after event endAt");
        }
        this.tasks.add(task);
    }

    public void markReminderSent() {
        this.reminderSent = true;
    }

    public void softDelete() {
        archive();
    }

    public boolean hasMissingDocuments() {
        return requiredDocuments.stream().anyMatch(requiredDocument -> !requiredDocument.isSubmitted());
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public EventType getType() {
        return type;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getLocation() {
        return location;
    }

    public CanonicalRef getCreatedByRef() {
        return createdByRef;
    }

    public CanonicalRef getTeamRef() {
        return teamRef;
    }

    public List<AttendeeRef> getAttendees() {
        return List.copyOf(attendees);
    }

    public List<RequiredDocument> getRequiredDocuments() {
        return List.copyOf(requiredDocuments);
    }

    public List<TaskAssignment> getTasks() {
        return List.copyOf(tasks);
    }

    public boolean isReminderSent() {
        return reminderSent;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartAt(Instant startAt) {
        validateTimeRange(startAt, this.endAt);
        this.startAt = startAt;
    }

    public void setEndAt(Instant endAt) {
        validateTimeRange(this.startAt, endAt);
        this.endAt = endAt;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    private static void validateTimeRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Event endAt must be after startAt");
        }
    }
}
