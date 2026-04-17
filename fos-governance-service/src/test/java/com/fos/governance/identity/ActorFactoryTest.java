package com.fos.governance.identity;

import com.fos.governance.identity.application.factory.CoachActorFactory;
import com.fos.governance.identity.application.factory.PlayerActorFactory;
import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.domain.ActorRole;
import com.fos.sdk.core.ResourceState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActorFactoryTest {

    @Test
    void should_create_player_actor_with_draft_state() {
        PlayerActorFactory factory = new PlayerActorFactory();
        Actor actor = factory.create("player@club.com", "Carlos", "Silva", UUID.randomUUID());

        assertThat(actor.getRole()).isEqualTo(ActorRole.PLAYER);
        assertThat(actor.getState()).isEqualTo(ResourceState.DRAFT);
        assertThat(actor.getEmail()).isEqualTo("player@club.com");
    }

    @Test
    void should_create_coach_actor_with_draft_state() {
        CoachActorFactory factory = new CoachActorFactory();
        Actor actor = factory.create("coach@club.com", "Marco", "Rossi", UUID.randomUUID());

        assertThat(actor.getRole()).isEqualTo(ActorRole.HEAD_COACH);
        assertThat(actor.getState()).isEqualTo(ResourceState.DRAFT);
    }
}
