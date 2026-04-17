package com.fos.governance.canonical.infrastructure.persistence;

import com.fos.governance.canonical.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findByNameIgnoreCaseAndCountryIgnoreCase(String name, String country);
}
