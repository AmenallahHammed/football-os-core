package com.fos.governance.identity.domain;

import com.fos.sdk.core.ResourceState;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * An Actor is any human user of Football OS: player, coach, admin, analyst.
 * Identity is owned by Keycloak; this entity mirrors what we need for authorization and signals.
 * Actor belongs to the fos_identity schema — not accessible from domain services.
 */
@Entity
@Table(schema = "fos_identity", name = "actor")
@EntityListeners(AuditingEntityListener.class)
public class Actor {

    @Id
    @Column(name = "resource_id")
    private UUID resourceId = UUID.randomUUID();

    @Column(name = "keycloak_user_id", unique = true)
    private String keycloakUserId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ActorRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ResourceState state = ResourceState.DRAFT;

    @Column(name = "club_id")
    private UUID clubId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    protected Actor() {}

    public Actor(String email, String firstName, String lastName, ActorRole role, UUID clubId) {
        this.resourceId = UUID.randomUUID();
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.clubId = clubId;
        this.state = ResourceState.DRAFT;
    }

    public void activate() {
        if (this.state != ResourceState.DRAFT) {
            throw new IllegalStateException("Only DRAFT actors can be activated");
        }
        this.state = ResourceState.ACTIVE;
    }

    public void deactivate() {
        if (this.state == ResourceState.ARCHIVED) {
            throw new IllegalStateException("Already deactivated");
        }
        this.state = ResourceState.ARCHIVED;
    }

    public void syncKeycloakId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
        if (this.state == ResourceState.DRAFT) {
            this.state = ResourceState.ACTIVE;
        }
    }

    // Getters — no setters; mutations go through domain methods above
    public UUID getResourceId()       { return resourceId; }
    public String getKeycloakUserId() { return keycloakUserId; }
    public String getEmail()          { return email; }
    public String getFirstName()      { return firstName; }
    public String getLastName()       { return lastName; }
    public ActorRole getRole()        { return role; }
    public ResourceState getState()   { return state; }
    public UUID getClubId()           { return clubId; }
    public Instant getCreatedAt()     { return createdAt; }
    public Instant getUpdatedAt()     { return updatedAt; }
    public Long getVersion()          { return version; }
}
