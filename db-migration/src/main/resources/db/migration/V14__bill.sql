CREATE TABLE bill (
    id                           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                    uuid NOT NULL,
    bill_service_code            varchar(60) NOT NULL,
    biller_id                    varchar(60),
    biller_name                  varchar(150),
    customer_bill_account_no     varchar(100) NOT NULL,
    customer_name                varchar(150),
    customer_mobile_number       varchar(20),
    amount                       numeric(14,2),
    amount_breakdown             text,
    bill_number                  varchar(100),
    bill_date                    varchar(30),
    due_date                     varchar(30),
    bill_period                  varchar(30),
    payment_status               varchar(20) NOT NULL,
    bill_status                  varchar(20) NOT NULL,
    last_fetch_response_snapshot text,
    last_fetch_at                timestamptz,
    last_fetch_failed            boolean NOT NULL DEFAULT false,
    last_fetch_failure_reason    varchar(300),
    auto_fetch_enabled           boolean NOT NULL DEFAULT true,
    auto_pay_enabled             boolean NOT NULL DEFAULT false,
    last_paid_due_date           varchar(30),
    created_by                   varchar(50),
    created_at                   timestamptz NOT NULL DEFAULT now(),
    updated_at                   timestamptz NOT NULL DEFAULT now(),
    provider_code                varchar(32),
    provider_category_id         varchar(64),
    provider_location_id         varchar(64),
    provider_biller_id           varchar(64),
    fetch_parameters             text,
    CONSTRAINT uq_bill_client_service_account
        UNIQUE (client_id, bill_service_code, customer_bill_account_no)
);

CREATE INDEX idx_bill_client_created ON bill (client_id, created_at DESC);
CREATE INDEX idx_bill_client_status ON bill (client_id, bill_status);
CREATE INDEX idx_bill_auto_fetch ON bill (client_id) WHERE auto_fetch_enabled;
