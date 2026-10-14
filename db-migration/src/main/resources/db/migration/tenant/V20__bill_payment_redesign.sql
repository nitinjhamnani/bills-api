-- Splits the old single "bill" table (dual wallet/settlement status, one row per submission) into
-- three tables: bill (one row per unique managed bill, always the latest cycle's data),
-- bill_payment (one row per fetch/payment cycle that actually became payable), and
-- bill_payment_log (a full timestamped activity trail). Dev data only - no backfill.

DROP TABLE IF EXISTS bill_dispute;
DROP TABLE IF EXISTS bill CASCADE;
DROP TABLE IF EXISTS customer_bill_account;

CREATE TABLE bill (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                       UUID NOT NULL,
    bill_service_code               VARCHAR(60) NOT NULL,
    biller_id                       VARCHAR(60),
    biller_name                     VARCHAR(150),
    customer_bill_account_no        VARCHAR(100) NOT NULL,
    customer_name                   VARCHAR(150),
    customer_mobile_number          VARCHAR(20),
    amount                          NUMERIC(14,2),
    amount_breakdown                TEXT,
    bill_number                     VARCHAR(100),
    bill_date                       VARCHAR(30),
    due_date                        VARCHAR(30),
    bill_period                     VARCHAR(30),
    payment_status                  VARCHAR(20) NOT NULL,
    bill_status                     VARCHAR(20) NOT NULL,
    last_fetch_response_snapshot    TEXT,
    last_fetch_at                   TIMESTAMPTZ,
    last_fetch_failed               BOOLEAN NOT NULL DEFAULT FALSE,
    last_fetch_failure_reason       VARCHAR(300),
    auto_fetch_enabled              BOOLEAN NOT NULL DEFAULT TRUE,
    auto_pay_enabled                BOOLEAN NOT NULL DEFAULT FALSE,
    auto_pay_max_amount             NUMERIC(14,2),
    last_paid_due_date              VARCHAR(30),
    created_by                      VARCHAR(50),
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bill_client_service_account UNIQUE (client_id, bill_service_code, customer_bill_account_no)
);

CREATE TABLE bill_payment (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id                     UUID NOT NULL REFERENCES bill(id),
    client_id                   UUID NOT NULL,
    amount                      NUMERIC(14,2) NOT NULL,
    amount_breakdown            TEXT,
    bill_number                 VARCHAR(100),
    bill_date                   VARCHAR(30),
    due_date                    VARCHAR(30),
    bill_period                 VARCHAR(30),
    api_response_snapshot       TEXT,
    org_status                  VARCHAR(20) NOT NULL,
    payment_status              VARCHAR(20) NOT NULL,
    bill_status                 VARCHAR(20) NOT NULL,
    idempotency_key             VARCHAR(150) NOT NULL UNIQUE,
    client_paid_at              TIMESTAMPTZ,
    client_paid_by              VARCHAR(50),
    org_picked_up_at            TIMESTAMPTZ,
    org_picked_up_by            VARCHAR(50),
    org_paid_at                 TIMESTAMPTZ,
    org_paid_by                 VARCHAR(50),
    bbps_transaction_id         VARCHAR(100),
    external_transaction_id     VARCHAR(100),
    bill_type                   VARCHAR(50),
    bill_pay_failure_reason     VARCHAR(300),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bill_payment_bill_id ON bill_payment(bill_id);
CREATE INDEX idx_bill_payment_client_id ON bill_payment(client_id);
CREATE INDEX idx_bill_payment_org_status ON bill_payment(org_status);
CREATE INDEX idx_bill_payment_payment_status ON bill_payment(payment_status);

CREATE TABLE bill_payment_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id             UUID NOT NULL REFERENCES bill(id),
    bill_payment_id     UUID REFERENCES bill_payment(id),
    event_type          VARCHAR(40) NOT NULL,
    description         VARCHAR(500),
    actor               VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bill_payment_log_bill_id ON bill_payment_log(bill_id);

CREATE TABLE bill_dispute (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_payment_id     UUID NOT NULL REFERENCES bill_payment(id),
    raised_by           VARCHAR(50),
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'RAISED',
    resolution_notes    VARCHAR(500),
    resolved_by         VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

-- The bill-payment maker-checker approval gate is dropped in favor of the simpler
-- NA -> PENDING -> IN_PROCESS -> PAID flow; only wallet top-up approval remains.
DELETE FROM approval_rule WHERE action_type = 'BILL_PAYMENT';
