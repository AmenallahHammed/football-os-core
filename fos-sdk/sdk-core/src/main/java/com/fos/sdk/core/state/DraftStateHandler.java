package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public class DraftStateHandler implements ResourceStateHandler {
    public boolean canEdit()     { return true; }
    public boolean canActivate() { return true; }
    public boolean canArchive()  { return false; }
    public boolean canShare()    { return false; }

    public ResourceState transitionTo(ResourceState target) {
        if (target == ResourceState.ACTIVE) return ResourceState.ACTIVE;
        throw new IllegalStateException("Cannot transition from DRAFT to " + target);
    }
}
