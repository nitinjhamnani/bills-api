CREATE TABLE organisation_category_config (
    id          uuid PRIMARY KEY,
    category_id varchar(255) NOT NULL,
    enabled     boolean NOT NULL,
    enabled_by  varchar(255),
    enabled_at  timestamptz,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT organisation_category_config_category_key UNIQUE (category_id)
);

CREATE INDEX organisation_category_config_enabled_idx ON organisation_category_config (enabled);
