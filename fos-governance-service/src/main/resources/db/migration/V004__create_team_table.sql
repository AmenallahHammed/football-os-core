CREATE TABLE fos_canonical.team (
    resource_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL UNIQUE,
    short_name     VARCHAR(50),
    country        VARCHAR(10),
    club_id        UUID,
    state          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255)
);
