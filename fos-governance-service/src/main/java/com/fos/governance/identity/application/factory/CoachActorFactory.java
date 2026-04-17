package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;

public class CoachActorFactory extends ActorFactory {
    @Override
    protected ActorRole defaultRole() { return ActorRole.HEAD_COACH; }
}
