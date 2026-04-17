package com.fos.governance.canonical.application;

import com.fos.governance.canonical.api.TeamRequest;
import com.fos.governance.canonical.domain.Team;
import com.fos.governance.canonical.infrastructure.persistence.TeamRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.TeamDTO;
import com.fos.sdk.events.FosKafkaProducer;
import com.fos.sdk.events.KafkaTopics;
import com.fos.sdk.events.SignalEnvelope;
import com.fos.sdk.events.SignalType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final FosKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;

    public TeamService(TeamRepository teamRepository,
                       FosKafkaProducer kafkaProducer,
                       ObjectMapper objectMapper) {
        this.teamRepository = teamRepository;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    public Team createTeam(TeamRequest request) {
        Team team = new Team(request.name(), request.shortName(),
                             request.country(), request.clubId());
        Team saved = teamRepository.save(team);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.CANONICAL_TEAM_CREATED)
                .actorRef(CanonicalRef.of(CanonicalType.TEAM, saved.getTeamId()).toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "teamId",  saved.getTeamId().toString(),
                        "name",    saved.getName(),
                        "country", saved.getCountry() != null ? saved.getCountry() : ""
                )))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Team getTeam(UUID id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Team> findByName(String name, String country) {
        return teamRepository.findByNameIgnoreCaseAndCountryIgnoreCase(name, country);
    }

    public static TeamDTO toDTO(Team t) {
        return new TeamDTO(t.getTeamId(), t.getName(), t.getShortName(),
                           t.getCountry(), t.getClubId());
    }
}
