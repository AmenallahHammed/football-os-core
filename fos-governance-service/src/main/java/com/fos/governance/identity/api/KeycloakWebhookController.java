package com.fos.governance.identity.api;

import com.fos.governance.identity.application.ActorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives Keycloak Admin Events and User Events via webhook.
 */
@RestController
@RequestMapping("/api/v1/identity/keycloak")
public class KeycloakWebhookController {

    private static final Logger log = LoggerFactory.getLogger(KeycloakWebhookController.class);
    private static final String DEFAULT_SECRET_HEADER = "X-Keycloak-Webhook-Secret";

    private final ActorService actorService;
    private final String webhookSecret;

    public KeycloakWebhookController(
            ActorService actorService,
            @Value("${fos.identity.keycloak.webhook.secret}") String webhookSecret) {
        this.actorService = actorService;
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("KEYCLOAK_WEBHOOK_SECRET must be configured");
        }
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleEvent(
            @RequestHeader(name = DEFAULT_SECRET_HEADER, required = false) String providedSecret,
            @RequestBody Map<String, Object> event) {
        if (!isAuthorized(providedSecret)) {
            log.warn("Rejected Keycloak webhook: invalid or missing secret header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String type = (String) event.get("type");
        if ("REGISTER".equals(type) || "LOGIN".equals(type)) {
            String keycloakUserId = (String) event.get("userId");
            @SuppressWarnings("unchecked")
            Map<String, String> details = (Map<String, String>) event.getOrDefault("details", Map.of());
            String email = details.get("email");
            if (keycloakUserId != null && email != null) {
                actorService.syncKeycloakUser(keycloakUserId, email);
            } else {
                log.warn("Keycloak webhook missing userId or email for event type={}", type);
            }
        }
        return ResponseEntity.ok().build();
    }

    private boolean isAuthorized(String providedSecret) {
        if (providedSecret == null || providedSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
