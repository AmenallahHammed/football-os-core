package com.fos.governance.identity.infrastructure.persistence;

import com.fos.governance.identity.domain.Actor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActorRepository extends JpaRepository<Actor, UUID> {
    Optional<Actor> findByEmail(String email);
    Optional<Actor> findByKeycloakUserId(String keycloakUserId);
}
