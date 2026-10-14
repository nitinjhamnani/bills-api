-- Provider-agnostic BBPS master data. category_id and location_id are text so a zero-padded
-- location id ("08") is never coerced through an integer. Rows the provider stops returning are
-- marked inactive rather than deleted, so bills already created against them keep their history.

CREATE TABLE platform.bbps_category (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code varchar(32) NOT NULL,
    category_id varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    category_group varchar(32),
    active boolean NOT NULL DEFAULT true,
    synced_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_bbps_category UNIQUE (provider_code, category_id)
);

CREATE TABLE platform.bbps_location (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code varchar(32) NOT NULL,
    location_id varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    abbreviation varchar(16),
    active boolean NOT NULL DEFAULT true,
    synced_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_bbps_location UNIQUE (provider_code, location_id)
);

-- Bill-fetch calls only. Catalog lookups are high-volume and stay in the application log.
CREATE TABLE platform.provider_api_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz NOT NULL DEFAULT now(),
    provider varchar(32) NOT NULL,
    operation varchar(64) NOT NULL,
    tenant varchar(128),
    bill_id varchar(64),
    client_ref_id varchar(128),
    http_status integer,
    response_code varchar(16),
    duration_ms bigint,
    masked_request text,
    truncated_response text
);

CREATE INDEX idx_provider_api_log_created ON platform.provider_api_log (created_at DESC);
CREATE INDEX idx_provider_api_log_bill ON platform.provider_api_log (bill_id);
