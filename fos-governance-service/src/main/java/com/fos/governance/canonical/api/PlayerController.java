package com.fos.governance.canonical.api;

import com.fos.governance.canonical.application.PlayerService;
import com.fos.sdk.canonical.PlayerDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerDTO createPlayer(@Valid @RequestBody PlayerRequest request) {
        return PlayerService.toDTO(playerService.createPlayer(request));
    }

    @GetMapping("/{id}")
    public PlayerDTO getPlayer(@PathVariable UUID id) {
        return PlayerService.toDTO(playerService.getPlayer(id));
    }

    @PutMapping("/{id}")
    public PlayerDTO updatePlayer(@PathVariable UUID id, @Valid @RequestBody PlayerRequest request) {
        return PlayerService.toDTO(playerService.updatePlayer(id, request));
    }

    @GetMapping("/find")
    public PlayerDTO findByIdentity(
            @RequestParam String name,
            @RequestParam LocalDate dob,
            @RequestParam String nationality) {
        return playerService.findByIdentity(name, dob, nationality)
                .map(PlayerService::toDTO)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Player not found: " + name));
    }
}
