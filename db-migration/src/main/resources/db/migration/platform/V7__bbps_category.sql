CREATE TABLE platform.bbps_category (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code   varchar(32) NOT NULL,
    category_id     varchar(64) NOT NULL,
    name            varchar(200) NOT NULL,
    category_group  varchar(32),
    active          boolean NOT NULL DEFAULT true,
    synced_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_bbps_category UNIQUE (provider_code, category_id)
);

CREATE INDEX idx_bbps_category_active_name
    ON platform.bbps_category (name) WHERE active;
CREATE INDEX idx_bbps_category_id_active
    ON platform.bbps_category (category_id) WHERE active;
CREATE INDEX idx_bbps_category_provider
    ON platform.bbps_category (provider_code);
