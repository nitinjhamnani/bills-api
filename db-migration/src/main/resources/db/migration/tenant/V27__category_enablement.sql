-- Categories replace the old bill-service catalog as the thing an organisation and a client switch
-- on. No row means disabled, so nothing is seeded: a new organisation starts with every category off.
-- The dropped tables only ever held that on/off switch.

DROP TABLE IF EXISTS client_bill_service_config;
DROP TABLE IF EXISTS organisation_bill_service_config;

CREATE TABLE organisation_category_config (
    id uuid PRIMARY KEY,
    category_id varchar(64) NOT NULL UNIQUE,
    enabled boolean NOT NULL,
    enabled_by varchar(32),
    enabled_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE client_category_config (
    id uuid PRIMARY KEY,
    client_id uuid NOT NULL REFERENCES client (id),
    category_id varchar(64) NOT NULL,
    enabled boolean NOT NULL,
    enabled_by varchar(32),
    enabled_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_client_category UNIQUE (client_id, category_id)
);

ALTER TABLE bill ADD COLUMN IF NOT EXISTS provider_code varchar(32);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS provider_category_id varchar(64);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS provider_location_id varchar(64);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS provider_biller_id varchar(64);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS fetch_parameters text;
