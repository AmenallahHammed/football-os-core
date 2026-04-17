package com.fos.governance.identity.api;

import com.fos.governance.identity.application.ActorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/actors")
public class ActorController {

    private final ActorService actorService;

    public ActorController(ActorService actorService) {
        this.actorService = actorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActorResponse createActor(@Valid @RequestBody ActorRequest request) {
        return ActorResponse.from(actorService.createActor(request));
    }

    @GetMapping("/{id}")
    public ActorResponse getActor(@PathVariable UUID id) {
        return ActorResponse.from(actorService.getActor(id));
    }

    @PutMapping("/{id}")
    public ActorResponse updateActor(@PathVariable UUID id, @Valid @RequestBody ActorRequest request) {
        return ActorResponse.from(actorService.updateActor(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateActor(@PathVariable UUID id) {
        actorService.deactivateActor(id);
    }
}
