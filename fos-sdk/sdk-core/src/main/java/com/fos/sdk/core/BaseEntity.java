package com.fos.sdk.core;

import com.fos.sdk.core.state.ResourceStateHandler;
import com.fos.sdk.core.state.ResourceStateHandlerFactory;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceState state = ResourceState.DRAFT;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (resourceId == null) resourceId = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public ResourceStateHandler stateHandler() {
        return ResourceStateHandlerFactory.forState(state);
    }

    public void activate() {
        if (!stateHandler().canActivate()) {
            throw new IllegalStateException("Cannot activate resource in state: " + state);
        }
        this.state = ResourceState.ACTIVE;
    }

    public void archive() {
        if (!stateHandler().canArchive()) {
            throw new IllegalStateException("Cannot archive resource in state: " + state);
        }
        this.state = ResourceState.ARCHIVED;
    }

    public UUID getResourceId() { return resourceId; }
    public ResourceState getState() { return state; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
