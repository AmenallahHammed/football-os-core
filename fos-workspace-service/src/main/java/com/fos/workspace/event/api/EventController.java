package com.fos.workspace.event.api;

import com.fos.workspace.event.application.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{eventId}")
    public EventResponse getEvent(@PathVariable UUID eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping
    public Page<EventResponse> listEventsByTeam(@RequestParam UUID teamRefId,
                                                @PageableDefault(size = 20) Pageable pageable) {
        return eventService.listEventsByTeam(teamRefId, pageable);
    }

    @PutMapping("/{eventId}")
    public EventResponse updateEvent(@PathVariable UUID eventId,
                                     @RequestBody UpdateEventRequest request) {
        return eventService.updateEvent(eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable UUID eventId) {
        eventService.deleteEvent(eventId);
    }
}
