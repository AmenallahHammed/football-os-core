package com.fos.governance.canonical.domain;

import com.fos.sdk.core.ResourceState;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "fos_canonical", name = "team")
@EntityListeners(AuditingEntityListener.class)
public class Team {

    @Id
    @Column(name = "resource_id")
    private UUID teamId = UUID.randomUUID();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Column(name = "country")
    private String country;

    @Column(name = "club_id")
    private UUID clubId;

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

    protected Team() {}

    public Team(String name, String shortName, String country, UUID clubId) {
        this.teamId = UUID.randomUUID();
        this.name = name;
        this.shortName = shortName;
        this.country = country;
        this.clubId = clubId;
        this.state = ResourceState.ACTIVE;
    }

    public void updateProfile(String name, String shortName, String country, UUID clubId) {
        this.name = name;
        this.shortName = shortName;
        this.country = country;
        this.clubId = clubId;
    }

    // Getters
    public UUID getTeamId()         { return teamId; }
    public String getName()         { return name; }
    public String getShortName()    { return shortName; }
    public String getCountry()      { return country; }
    public UUID getClubId()         { return clubId; }
    public ResourceState getState() { return state; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }
    public Long getVersion()        { return version; }
}
