CREATE TABLE organisation_location_config (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id     varchar(64) NOT NULL,
    enabled         boolean NOT NULL,
    enabled_by      varchar(32),
    enabled_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organisation_location_config_location_id_key UNIQUE (location_id)
);
