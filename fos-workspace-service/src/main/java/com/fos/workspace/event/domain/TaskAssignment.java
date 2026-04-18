package com.fos.workspace.event.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A task assigned to an actor as part of an event.
 */
public class TaskAssignment {

    private UUID taskId;
    private String title;
    private String description;
    private UUID assignedToActorId;
    private Instant dueAt;
    private boolean completed;
    private Instant completedAt;

    protected TaskAssignment() {
    }

    public TaskAssignment(String title, String description, UUID assignedToActorId, Instant dueAt) {
        this.taskId = UUID.randomUUID();
        this.title = title;
        this.description = description;
        this.assignedToActorId = assignedToActorId;
        this.dueAt = dueAt;
        this.completed = false;
    }

    public void complete() {
        this.completed = true;
        this.completedAt = Instant.now();
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAssignedToActorId() {
        return assignedToActorId;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
