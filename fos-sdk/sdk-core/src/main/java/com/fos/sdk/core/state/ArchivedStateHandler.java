package com.fos.sdk.core.state;

import com.fos.sdk.core.ResourceState;

public class ArchivedStateHandler implements ResourceStateHandler {
    public boolean canEdit()     { return false; }
    public boolean canActivate() { return false; }
    public boolean canArchive()  { return false; }
    public boolean canShare()    { return false; }

    public ResourceState transitionTo(ResourceState target) {
        throw new IllegalStateException("Cannot transition from ARCHIVED to " + target);
    }
}
