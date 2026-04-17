CREATE TABLE fos_identity.actor (
    resource_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id  VARCHAR(255) UNIQUE,
    email             VARCHAR(255) NOT NULL UNIQUE,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    role              VARCHAR(50)  NOT NULL,
    state             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    club_id           UUID,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255)
);

CREATE INDEX idx_actor_email ON fos_identity.actor(email);
CREATE INDEX idx_actor_keycloak ON fos_identity.actor(keycloak_user_id);
