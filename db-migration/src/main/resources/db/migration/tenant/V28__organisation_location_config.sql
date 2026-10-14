-- Organisation on/off switch for a synced location. No row means disabled.
CREATE TABLE organisation_location_config (
    id uuid PRIMARY KEY,
    location_id varchar(64) NOT NULL UNIQUE,
    enabled boolean NOT NULL,
    enabled_by varchar(32),
    enabled_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
