CREATE TABLE bbps_location (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code   varchar(32) NOT NULL,
    location_id     varchar(64) NOT NULL,
    name            varchar(200) NOT NULL,
    abbreviation    varchar(16),
    active          boolean NOT NULL DEFAULT true,
    synced_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_bbps_location UNIQUE (provider_code, location_id)
);

CREATE INDEX idx_bbps_location_active_name
    ON bbps_location (name) WHERE active;
CREATE INDEX idx_bbps_location_id_active
    ON bbps_location (location_id) WHERE active;
CREATE INDEX idx_bbps_location_provider
    ON bbps_location (provider_code);
