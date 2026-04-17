package com.fos.governance.canonical.infrastructure.persistence;

import com.fos.governance.canonical.domain.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByNameAndDateOfBirthAndNationality(
            String name, LocalDate dateOfBirth, String nationality);

    boolean existsByNameAndDateOfBirthAndNationality(
            String name, LocalDate dateOfBirth, String nationality);
}
