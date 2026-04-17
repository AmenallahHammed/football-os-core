package com.fos.governance.canonical.application;

import com.fos.governance.canonical.api.PlayerRequest;
import com.fos.governance.canonical.domain.Player;
import com.fos.governance.canonical.infrastructure.persistence.PlayerRepository;
import com.fos.sdk.canonical.CanonicalRef;
import com.fos.sdk.canonical.CanonicalType;
import com.fos.sdk.canonical.PlayerDTO;
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
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final FosKafkaProducer kafkaProducer;
    private final ObjectMapper objectMapper;

    public PlayerService(PlayerRepository playerRepository,
                         FosKafkaProducer kafkaProducer,
                         ObjectMapper objectMapper) {
        this.playerRepository = playerRepository;
        this.kafkaProducer = kafkaProducer;
        this.objectMapper = objectMapper;
    }

    public Player createPlayer(PlayerRequest request) {
        Player player = new Player(
                request.name(), request.position(),
                request.nationality(), request.dateOfBirth(), request.currentTeamId());

        Player saved = playerRepository.save(player);

        kafkaProducer.emit(SignalEnvelope.builder()
                .type(SignalType.FACT)
                .topic(KafkaTopics.CANONICAL_PLAYER_CREATED)
                .actorRef(CanonicalRef.of(CanonicalType.PLAYER, saved.getPlayerId()).toString())
                .payload(objectMapper.valueToTree(Map.of(
                        "playerId",    saved.getPlayerId().toString(),
                        "name",        saved.getName(),
                        "position",    saved.getPosition() != null ? saved.getPosition() : "",
                        "nationality", saved.getNationality() != null ? saved.getNationality() : ""
                )))
                .build());

        return saved;
    }

    @Transactional(readOnly = true)
    public Player getPlayer(UUID id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<Player> findByIdentity(String name,
                                           java.time.LocalDate dob,
                                           String nationality) {
        return playerRepository.findByNameAndDateOfBirthAndNationality(name, dob, nationality);
    }

    public static PlayerDTO toDTO(Player p) {
        return new PlayerDTO(
                p.getPlayerId(), p.getName(), p.getPosition(),
                p.getNationality(), p.getDateOfBirth(), p.getCurrentTeamId());
    }
}
