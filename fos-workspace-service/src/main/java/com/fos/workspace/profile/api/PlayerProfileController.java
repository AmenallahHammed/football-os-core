package com.fos.workspace.profile.api;

import com.fos.workspace.profile.application.PlayerProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
public class PlayerProfileController {

    private final PlayerProfileService playerProfileService;

    public PlayerProfileController(PlayerProfileService playerProfileService) {
        this.playerProfileService = playerProfileService;
    }

    @GetMapping("/players/{playerId}")
    public PlayerProfileResponse getPlayerProfile(@PathVariable UUID playerId) {
        return playerProfileService.getProfile(playerId);
    }
}
