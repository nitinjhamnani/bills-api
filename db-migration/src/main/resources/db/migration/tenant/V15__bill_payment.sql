CREATE TABLE bill_payment (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id                 uuid NOT NULL,
    client_id               uuid NOT NULL,
    amount                  numeric(14,2) NOT NULL,
    amount_breakdown        text,
    bill_number             varchar(100),
    bill_date               varchar(30),
    due_date                varchar(30),
    bill_period             varchar(30),
    api_response_snapshot   text,
    org_status              varchar(20) NOT NULL,
    payment_status          varchar(20) NOT NULL,
    bill_status             varchar(20) NOT NULL,
    idempotency_key         varchar(150) NOT NULL,
    client_paid_at          timestamptz,
    client_paid_by          varchar(50),
    org_picked_up_at        timestamptz,
    org_picked_up_by        varchar(50),
    org_paid_at             timestamptz,
    org_paid_by             varchar(50),
    bbps_transaction_id     varchar(100),
    external_transaction_id varchar(100),
    bill_type               varchar(50),
    bill_pay_failure_reason varchar(300),
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bill_payment_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT bill_payment_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES bill (id)
);

CREATE INDEX idx_bill_payment_bill_created ON bill_payment (bill_id, created_at DESC);
CREATE INDEX idx_bill_payment_client_created ON bill_payment (client_id, created_at DESC);
CREATE INDEX idx_bill_payment_client_status_created
    ON bill_payment (client_id, payment_status, created_at DESC);
CREATE INDEX idx_bill_payment_org_status_created
    ON bill_payment (org_status, created_at DESC);
CREATE INDEX idx_bill_payment_client_org_status
    ON bill_payment (client_id, org_status, created_at DESC);
CREATE INDEX idx_bill_payment_created ON bill_payment (created_at DESC);
CREATE INDEX idx_bill_payment_client_paid ON bill_payment (client_id, client_paid_at DESC);
CREATE INDEX idx_bill_payment_org_paid ON bill_payment (client_id, org_paid_at DESC);
CREATE INDEX idx_bill_payment_client_paid_at ON bill_payment (client_paid_at DESC);
CREATE INDEX idx_bill_payment_org_paid_at ON bill_payment (org_paid_at DESC);
