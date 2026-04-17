CREATE TABLE fos_canonical.player (
    resource_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    position       VARCHAR(50),
    nationality    VARCHAR(10),
    date_of_birth  DATE,
    current_team_id UUID,
    state          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255)
);

CREATE INDEX idx_player_name_dob ON fos_canonical.player(name, date_of_birth);
