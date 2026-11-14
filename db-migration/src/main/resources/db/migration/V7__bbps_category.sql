CREATE TABLE bbps_category (
    id              uuid PRIMARY KEY,
    provider_code   varchar(32) NOT NULL,
    category_id     varchar(64) NOT NULL,
    name            varchar(200) NOT NULL,
    category_group  varchar(32),
    active          boolean NOT NULL DEFAULT true,
    synced_at       timestamptz NOT NULL,
    CONSTRAINT bbps_category_provider_category_key UNIQUE (provider_code, category_id)
);

CREATE INDEX bbps_category_active_name_idx ON bbps_category (name) WHERE active;
CREATE INDEX bbps_category_id_active_idx ON bbps_category (category_id) WHERE active;
CREATE INDEX bbps_category_provider_idx ON bbps_category (provider_code);
