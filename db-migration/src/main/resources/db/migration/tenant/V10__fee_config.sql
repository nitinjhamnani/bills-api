CREATE TABLE fee_config (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_service_code   varchar(30),
    fee_type            varchar(10) NOT NULL,
    value               numeric(10,4) NOT NULL,
    enabled             boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fee_config_fee_type_check CHECK (fee_type IN ('FLAT', 'PERCENTAGE'))
);

CREATE INDEX idx_fee_config_service ON fee_config (bill_service_code) WHERE enabled;
