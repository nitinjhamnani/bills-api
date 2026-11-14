CREATE TABLE fee_config (
    id                uuid PRIMARY KEY,
    bill_service_code varchar(255),
    fee_type          varchar(255) NOT NULL,
    value             numeric(10, 4) NOT NULL,
    enabled           boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT fee_config_value_check CHECK (value >= 0)
);

CREATE INDEX fee_config_service_idx ON fee_config (bill_service_code) WHERE enabled;
