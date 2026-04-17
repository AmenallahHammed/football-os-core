package com.fos.sdk.test;

import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.FosRoles;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockActorFactoryTest {

    @Test
    void should_create_player_actor_with_consistent_ids() throws Exception {
        TestActor actor = MockActorFactory.player();

        assertThat(actor.role()).isEqualTo(FosRoles.PLAYER);
        assertThat(actor.actorId()).isNotNull();
        assertThat(actor.canonicalRef().type()).isEqualTo(CanonicalType.PLAYER);
        assertThat(actor.canonicalRef().id()).isEqualTo(actor.actorId());

        SignedJWT jwt = SignedJWT.parse(actor.signedJwt());
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(actor.actorId().toString());
        assertThat(jwt.getJWTClaimsSet().getClaim("role")).isEqualTo(FosRoles.PLAYER);
    }

    @Test
    void should_create_head_coach_actor() throws Exception {
        TestActor actor = MockActorFactory.headCoach();

        assertThat(actor.role()).isEqualTo(FosRoles.HEAD_COACH);
        assertThat(actor.canonicalRef().type()).isEqualTo(CanonicalType.CLUB);

        SignedJWT jwt = SignedJWT.parse(actor.signedJwt());
        assertThat(jwt.getJWTClaimsSet().getClaim("role")).isEqualTo(FosRoles.HEAD_COACH);
    }

    @Test
    void should_create_two_distinct_actors_for_same_role() {
        TestActor a1 = MockActorFactory.player();
        TestActor a2 = MockActorFactory.player();

        assertThat(a1.actorId()).isNotEqualTo(a2.actorId());
    }
}
