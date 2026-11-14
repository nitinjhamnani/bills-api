CREATE TABLE client_category_config (
    id          uuid PRIMARY KEY,
    client_id   varchar(8) NOT NULL,
    category_id varchar(255) NOT NULL,
    enabled     boolean NOT NULL,
    enabled_by  varchar(255),
    enabled_at  timestamptz,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT client_category_config_client_category_key UNIQUE (client_id, category_id)
);

CREATE INDEX client_category_config_client_idx ON client_category_config (client_id);
