CREATE TABLE client_category_config (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       uuid NOT NULL,
    category_id     varchar(64) NOT NULL,
    enabled         boolean NOT NULL,
    enabled_by      varchar(32),
    enabled_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_category UNIQUE (client_id, category_id),
    CONSTRAINT client_category_config_client_id_fkey FOREIGN KEY (client_id)
        REFERENCES client (id)
);
