package com.fos.governance.identity.application.factory;

import com.fos.governance.identity.domain.ActorRole;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class ActorFactoryRegistry {

    private final Map<ActorRole, ActorFactory> factories = new EnumMap<>(ActorRole.class);

    public ActorFactoryRegistry() {
        factories.put(ActorRole.PLAYER, new PlayerActorFactory());
        factories.put(ActorRole.HEAD_COACH, new CoachActorFactory());
        // All other roles use DefaultActorFactory
        for (ActorRole role : ActorRole.values()) {
            factories.computeIfAbsent(role, r -> new DefaultActorFactory(r));
        }
    }

    public ActorFactory forRole(ActorRole role) {
        return factories.get(role);
    }
}
