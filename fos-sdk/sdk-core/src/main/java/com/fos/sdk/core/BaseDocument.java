package com.fos.sdk.core;

import com.fos.sdk.core.state.ResourceStateHandler;
import com.fos.sdk.core.state.ResourceStateHandlerFactory;
import org.springframework.data.annotation.*;
import java.time.Instant;
import java.util.UUID;

public abstract class BaseDocument {

    @Id
    private String id;

    private UUID resourceId;
    private ResourceState state = ResourceState.DRAFT;

    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public void initId() {
        if (resourceId == null) {
            resourceId = UUID.randomUUID();
            id = resourceId.toString();
        }
    }

    public ResourceStateHandler stateHandler() {
        return ResourceStateHandlerFactory.forState(state);
    }

    public void activate() {
        if (!stateHandler().canActivate()) {
            throw new IllegalStateException("Cannot activate document in state: " + state);
        }
        this.state = ResourceState.ACTIVE;
    }

    public void archive() {
        if (!stateHandler().canArchive()) {
            throw new IllegalStateException("Cannot archive document in state: " + state);
        }
        this.state = ResourceState.ARCHIVED;
    }

    public String getId() { return id; }
    public UUID getResourceId() { return resourceId; }
    public ResourceState getState() { return state; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
