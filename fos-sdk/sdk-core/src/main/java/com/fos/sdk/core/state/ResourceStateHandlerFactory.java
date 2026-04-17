package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public final class ResourceStateHandlerFactory {
    private ResourceStateHandlerFactory() {}

    public static ResourceStateHandler forState(ResourceState state) {
        return switch (state) {
            case DRAFT    -> new DraftStateHandler();
            case ACTIVE   -> new ActiveStateHandler();
            case ARCHIVED -> new ArchivedStateHandler();
        };
    }
}
