package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public class ActiveStateHandler implements ResourceStateHandler {
    public boolean canEdit()     { return false; }
    public boolean canActivate() { return false; }
    public boolean canArchive()  { return true; }
    public boolean canShare()    { return true; }

    public ResourceState transitionTo(ResourceState target) {
        if (target == ResourceState.ARCHIVED) return ResourceState.ARCHIVED;
        throw new IllegalStateException("Cannot transition from ACTIVE to " + target);
    }
}
