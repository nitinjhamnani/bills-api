CREATE TABLE organisation_category_config (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id     varchar(64) NOT NULL,
    enabled         boolean NOT NULL,
    enabled_by      varchar(32),
    enabled_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organisation_category_config_category_id_key UNIQUE (category_id)
);
