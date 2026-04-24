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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EventService {

    private static final UUID FALLBACK_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FALLBACK_CLUB_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
        UUID clubId = currentClubId();
        String role = currentActorRole();
        CanonicalRef createdByRef = CanonicalRef.club(clubId);

        authorize("workspace.event.create", createdByRef, ResourceState.DRAFT.name(), actorId, role, clubId);

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
        UUID clubId = currentClubId();
        String role = currentActorRole();
        WorkspaceEvent event = loadEvent(eventId, clubId);
        authorize("workspace.event.read", resourceRefFor(event), event.getState().name(), actorId, role, clubId);
        return EventResponse.from(event);
    }

    public Page<EventResponse> listEventsByTeam(UUID teamRefId, Pageable pageable) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        authorize("workspace.event.read", CanonicalRef.team(teamRefId), ResourceState.ACTIVE.name(), actorId, role, clubId);

        return eventRepository.findByCreatedByRefIdAndTeamRefIdAndStateOrderByStartAtAsc(clubId, teamRefId, ResourceState.ACTIVE, pageable)
                .map(EventResponse::from);
    }

    public EventResponse updateEvent(UUID eventId, UpdateEventRequest request) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        WorkspaceEvent event = loadEvent(eventId, clubId);

        authorize("workspace.event.update", resourceRefFor(event), event.getState().name(), actorId, role, clubId);

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
                .actorRef(CanonicalRef.club(clubId).toString())
                .build());
        return EventResponse.from(saved);
    }

    public void deleteEvent(UUID eventId) {
        UUID actorId = currentActorId();
        UUID clubId = currentClubId();
        String role = currentActorRole();
        WorkspaceEvent event = loadEvent(eventId, clubId);

        authorize("workspace.event.delete", resourceRefFor(event), event.getState().name(), actorId, role, clubId);

        event.softDelete();
        eventRepository.save(event);
        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.AUDIT)
                .topic(KafkaTopics.AUDIT_ALL)
                .actorRef(CanonicalRef.club(clubId).toString())
                .build());
    }

    private WorkspaceEvent loadEvent(UUID eventId, UUID clubId) {
        return eventRepository.findByResourceIdAndCreatedByRefId(eventId, clubId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
    }

    private void authorize(String action,
                           CanonicalRef resourceRef,
                           String resourceState,
                           UUID actorId,
                           String role,
                           UUID clubId) {
        PolicyResult policy = policyClient.evaluate(PolicyRequest.withContext(
                actorId,
                role,
                action,
                resourceRef,
                resourceState,
                buildTenantPolicyContext(clubId)));
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

    private UUID currentClubId() {
        if (!securityEnabled) {
            return FALLBACK_CLUB_ID;
        }
        String clubId = securityContext.clubId();
        if (clubId == null || clubId.isBlank()) {
            throw new AccessDeniedException("Missing club context in token");
        }
        try {
            return UUID.fromString(clubId);
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid club context in token");
        }
    }

    private String currentActorRole() {
        return securityEnabled ? securityContext.getRole() : FALLBACK_ROLE;
    }

    private Map<String, Object> buildTenantPolicyContext(UUID clubId) {
        Map<String, Object> tenant = new HashMap<>();
        tenant.put("clubId", clubId.toString());

        Map<String, Object> context = new HashMap<>();
        context.put("tenant", tenant);
        return context;
    }
}
