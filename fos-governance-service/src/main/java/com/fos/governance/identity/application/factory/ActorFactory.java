package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.domain.ActorRole;
import java.util.UUID;

/**
 * Factory Method base. Each actor type subclass knows the correct default role
 * and any role-specific initialization. ActorService selects the right factory
 * via ActorFactoryRegistry — no switch(role) in service code.
 */
public abstract class ActorFactory {

    public final Actor create(String email, String firstName, String lastName, UUID clubId) {
        Actor actor = new Actor(email, firstName, lastName, defaultRole(), clubId);
        postCreate(actor);
        return actor;
    }

    protected abstract ActorRole defaultRole();

    /** Hook for role-specific initialization. Default: no-op. */
    protected void postCreate(Actor actor) {}
}
