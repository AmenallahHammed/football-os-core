package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;

public class PlayerActorFactory extends ActorFactory {
    @Override
    protected ActorRole defaultRole() { return ActorRole.PLAYER; }
}
