CREATE TABLE platform.provider_api_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at          timestamptz NOT NULL DEFAULT now(),
    provider            varchar(32) NOT NULL,
    operation           varchar(64) NOT NULL,
    tenant              varchar(128),
    bill_id             varchar(64),
    client_ref_id       varchar(128),
    http_status         integer,
    response_code       varchar(16),
    duration_ms         bigint,
    masked_request      text,
    truncated_response  text
);

CREATE INDEX idx_provider_api_log_created
    ON platform.provider_api_log (created_at DESC);
CREATE INDEX idx_provider_api_log_bill
    ON platform.provider_api_log (bill_id);
