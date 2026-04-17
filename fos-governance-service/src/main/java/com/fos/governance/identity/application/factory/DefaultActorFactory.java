package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;

public class DefaultActorFactory extends ActorFactory {

    private final ActorRole role;

    public DefaultActorFactory(ActorRole role) {
        this.role = role;
    }

    @Override
    protected ActorRole defaultRole() { return role; }
}
