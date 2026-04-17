package com.fos.governance.canonical.domain;

import com.fos.sdk.core.ResourceState;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Canonical identity fact for a football player.
 */
@Entity
@Table(schema = "fos_canonical", name = "player")
@EntityListeners(AuditingEntityListener.class)
public class Player {

    @Id
    @Column(name = "resource_id")
    private UUID playerId = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "position")
    private String position;

    @Column(name = "nationality")
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "current_team_id")
    private UUID currentTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ResourceState state = ResourceState.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    protected Player() {}

    public Player(String name, String position, String nationality,
                  LocalDate dateOfBirth, UUID currentTeamId) {
        this.playerId = UUID.randomUUID();
        this.name = name;
        this.position = position;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
        this.currentTeamId = currentTeamId;
        this.state = ResourceState.ACTIVE;
    }

    public void updateTeam(UUID teamId) {
        this.currentTeamId = teamId;
    }

    public void updateProfile(String name, String position, String nationality,
                              LocalDate dateOfBirth, UUID currentTeamId) {
        this.name = name;
        this.position = position;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
        this.currentTeamId = currentTeamId;
    }

    // Getters
    public UUID getPlayerId()         { return playerId; }
    public String getName()           { return name; }
    public String getPosition()       { return position; }
    public String getNationality()    { return nationality; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public UUID getCurrentTeamId()    { return currentTeamId; }
    public ResourceState getState()   { return state; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
    public Long getVersion()          { return version; }
}
