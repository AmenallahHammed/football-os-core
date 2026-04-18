package com.fos.workspace.event.application;

import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.core.ResourceState;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fos.sdk.policy.PolicyClient;
import com.fos.sdk.policy.PolicyRequest;
import com.fos.sdk.policy.PolicyResult;
import com.fos.sdk.security.FosSecurityContext;
import com.fos.workspace.event.api.CreateEventRequest;
import com.fos.workspace.event.api.EventResponse;
import com.fos.workspace.event.api.UpdateEventRequest;
import com.fos.workspace.event.domain.AttendeeRef;
import com.fos.workspace.event.domain.RequiredDocument;
import com.fos.workspace.event.domain.TaskAssignment;
import com.fos.workspace.event.domain.WorkspaceEvent;
import com.fos.workspace.event.infrastructure.persistence.WorkspaceEventRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EventService {

    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FALLBACK_ROLE = "ROLE_CLUB_ADMIN";
    private static final String EVENT_CREATED_TOPIC = "fos.workspace.event.created";
    private static final String EVENT_UPDATED_TOPIC = "fos.workspace.event.updated";

    private final WorkspaceEventRepository eventRepository;
    private final PolicyClient policyClient;
    private final FosKafkaProducer kafkaProducer;
    private final FosSecurityContext securityContext;
    private final boolean securityEnabled;

    public EventService(WorkspaceEventRepository eventRepository,
                        PolicyClient policyClient,
                        FosKafkaProducer kafkaProducer,
                        FosSecurityContext securityContext,
                        @Value("${fos.security.enabled:true}") boolean securityEnabled) {
        this.eventRepository = eventRepository;
        this.policyClient = policyClient;
        this.kafkaProducer = kafkaProducer;
        this.securityContext = securityContext;
        this.securityEnabled = securityEnabled;
    }

    public EventResponse createEvent(CreateEventRequest request) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        CanonicalRef createdByRef = CanonicalRef.club(actorId);

        authorize("workspace.event.create", createdByRef, ResourceState.DRAFT.name(), actorId, role);

        CanonicalRef teamRef = request.teamRefId() != null ? CanonicalRef.team(request.teamRefId()) : null;
        WorkspaceEvent event = WorkspaceEvent.create(
                request.title(),
                request.description(),
                request.type(),
                request.startAt(),
                request.endAt(),
                request.location(),
                createdByRef,
                teamRef);

        if (request.attendees() != null) {
            for (CreateEventRequest.AttendeeInput attendee : request.attendees()) {
                CanonicalType type = "PLAYER".equals(attendee.canonicalType()) ? CanonicalType.PLAYER : CanonicalType.CLUB;
                event.addAttendee(new AttendeeRef(CanonicalRef.of(type, attendee.actorId()), attendee.mandatory()));
            }
        }

        if (request.requiredDocuments() != null) {
            for (CreateEventRequest.RequiredDocumentInput requiredDocument : request.requiredDocuments()) {
                event.addRequiredDocument(new RequiredDocument(
                        requiredDocument.description(),
                        requiredDocument.documentCategory(),
                        requiredDocument.assignedToActorId()));
            }
        }

        if (request.tasks() != null) {
            for (CreateEventRequest.TaskInput task : request.tasks()) {
                event.addTask(new TaskAssignment(task.title(), task.description(), task.assignedToActorId(), task.dueAt()));
            }
        }

        WorkspaceEvent saved = eventRepository.save(event);
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(EVENT_CREATED_TOPIC)
                .actorRef(createdByRef.toString())
                .build());
        return EventResponse.from(saved);
    }

    public EventResponse getEvent(UUID eventId) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        WorkspaceEvent event = loadEvent(eventId);
        authorize("workspace.event.read", resourceRefFor(event), event.getState().name(), actorId, role);
        return EventResponse.from(event);
    }

    public Page<EventResponse> listEventsByTeam(UUID teamRefId, Pageable pageable) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        authorize("workspace.event.read", CanonicalRef.team(teamRefId), ResourceState.ACTIVE.name(), actorId, role);

        return eventRepository.findByTeamRefIdAndStateOrderByStartAtAsc(teamRefId, ResourceState.ACTIVE, pageable)
                .map(EventResponse::from);
    }

    public EventResponse updateEvent(UUID eventId, UpdateEventRequest request) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        WorkspaceEvent event = loadEvent(eventId);

        authorize("workspace.event.update", resourceRefFor(event), event.getState().name(), actorId, role);

        if (request.title() != null) {
            event.setTitle(request.title());
        }
        if (request.description() != null) {
            event.setDescription(request.description());
        }
        if (request.startAt() != null) {
            event.setStartAt(request.startAt());
        }
        if (request.endAt() != null) {
            event.setEndAt(request.endAt());
        }
        if (request.location() != null) {
            event.setLocation(request.location());
        }

        WorkspaceEvent saved = eventRepository.save(event);
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(EVENT_UPDATED_TOPIC)
                .actorRef(CanonicalRef.club(actorId).toString())
                .build());
        return EventResponse.from(saved);
    }

    public void deleteEvent(UUID eventId) {
        UUID actorId = currentActorId();
        String role = currentActorRole();
        WorkspaceEvent event = loadEvent(eventId);

        authorize("workspace.event.delete", resourceRefFor(event), event.getState().name(), actorId, role);

        event.softDelete();
        eventRepository.save(event);
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(CanonicalRef.club(actorId).toString())
                .build());
    }

    private WorkspaceEvent loadEvent(UUID eventId) {
        return eventRepository.findByResourceId(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
    }

    private void authorize(String action,
                           CanonicalRef resourceRef,
                           String resourceState,
                           UUID actorId,
                           String role) {
        PolicyResult policy = policyClient.evaluate(PolicyRequest.of(actorId, role, action, resourceRef, resourceState));
        if (!policy.isAllowed()) {
            throw new AccessDeniedException(policy.reason());
        }
    }

    private CanonicalRef resourceRefFor(WorkspaceEvent event) {
        return event.getTeamRef() != null ? event.getTeamRef() : event.getCreatedByRef();
    }

    private UUID currentActorId() {
        return securityEnabled ? securityContext.getActorId() : FALLBACK_ACTOR_ID;
    }

    private String currentActorRole() {
        return securityEnabled ? securityContext.getRole() : FALLBACK_ROLE;
    }
}
