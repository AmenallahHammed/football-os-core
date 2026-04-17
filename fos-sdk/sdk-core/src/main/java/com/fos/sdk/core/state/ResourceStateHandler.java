package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public interface ResourceStateHandler {
    boolean canEdit();
    boolean canActivate();
    boolean canArchive();
    boolean canShare();
    ResourceState transitionTo(ResourceState target);
}
