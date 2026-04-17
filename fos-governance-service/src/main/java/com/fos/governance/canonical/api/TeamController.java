package com.fos.governance.canonical.api;

import com.fos.governance.canonical.application.TeamService;
import com.fos.sdk.canonical.TeamDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDTO createTeam(@Valid @RequestBody TeamRequest request) {
        return TeamService.toDTO(teamService.createTeam(request));
    }

    @GetMapping("/{id}")
    public TeamDTO getTeam(@PathVariable UUID id) {
        return TeamService.toDTO(teamService.getTeam(id));
    }

    @GetMapping("/find")
    public TeamDTO findByName(
            @RequestParam String name,
            @RequestParam String country) {
        return teamService.findByName(name, country)
                .map(TeamService::toDTO)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Team not found: " + name + " / " + country));
    }
}
