package com.fos.governance.identity.application;

import com.fos.governance.identity.api.ActorRequest;
import com.fos.governance.identity.application.factory.ActorFactoryRegistry;
import com.fos.governance.identity.domain.Actor;
import com.fos.governance.identity.infrastructure.persistence.ActorRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ActorService {

    private final ActorRepository actorRepository;
    private final ActorFactoryRegistry factoryRegistry;
    private final FosKafkaProducer kafkaProducer;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ActorService(ActorRepository actorRepository,
                        ActorFactoryRegistry factoryRegistry,
                        FosKafkaProducer kafkaProducer,
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.actorRepository = actorRepository;
        this.factoryRegistry = factoryRegistry;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    public Actor createActor(ActorRequest request) {
        Actor actor = factoryRegistry
                .forRole(request.role())
                .create(request.email(), request.firstName(), request.lastName(), request.clubId());

        Actor saved = actorRepository.save(actor);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.IDENTITY_ACTOR_CREATED)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB,
                        request.clubId() != null ? request.clubId() : UUID.randomUUID()).toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "actorId",    saved.getResourceId().toString(),
                        "email",      saved.getEmail(),
                        "role",       saved.getRole().name(),
                        "state",      saved.getState().name()
                )))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Actor getActor(UUID id) {
        return actorRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Actor not found: " + id));
    }

    public Actor updateActor(UUID id, ActorRequest request) {
        Actor actor = getActor(id);
        actor.updateProfile(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.role(),
                request.clubId());
        Actor saved = actorRepository.save(actor);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.IDENTITY_ACTOR_UPDATED)
                .actorRef(CanonicalRef.of(
                        CanonicalType.CLUB,
                        saved.getClubId() != null ? saved.getClubId() : UUID.randomUUID()).toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "actorId", saved.getResourceId().toString(),
                        "email", saved.getEmail(),
                        "role", saved.getRole().name(),
                        "state", saved.getState().name()
                )))
                .build());

        return saved;
    }

    public Actor deactivateActor(UUID id) {
        Actor actor = getActor(id);
        actor.deactivate();
        Actor saved = actorRepository.save(actor);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.IDENTITY_ACTOR_DEACTIVATED)
                .actorRef(CanonicalRef.of(CanonicalType.CLUB, 
                        actor.getClubId() != null ? actor.getClubId() : UUID.randomUUID()).toString())
                .payload(objectMapper.valueToTree(Map.of("actorId", saved.getResourceId().toString())))
                .build());

        return saved;
    }

    /** Called by KeycloakWebhookController when Keycloak fires a REGISTER event. */
    public void syncKeycloakUser(String keycloakUserId, String email) {
        actorRepository.findByEmail(email).ifPresent(actor -> {
            actor.syncKeycloakId(keycloakUserId);
            actorRepository.save(actor);
        });
    }
}
