-- Closes out the rest of implementation plan section 4.3: saved customer bill accounts per
-- agency, with per-account auto-fetch and auto-pay configuration. (Bill submission itself
-- doesn't require one of these to exist - it still accepts a bare account number/name - but
-- auto-fetch/auto-pay only apply to accounts saved here.)

CREATE TABLE IF NOT EXISTS customer_bill_account (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id                   UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    bill_service_code           VARCHAR(30) NOT NULL,
    customer_bill_account_no    VARCHAR(100) NOT NULL,
    customer_name               VARCHAR(150),
    auto_fetch_enabled          BOOLEAN NOT NULL DEFAULT FALSE,
    auto_pay_enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    auto_pay_max_amount         NUMERIC(14,2),
    last_fetched_amount_due     NUMERIC(14,2),
    last_fetched_due_date       VARCHAR(20),
    last_fetched_at             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (agency_id, bill_service_code, customer_bill_account_no)
);

CREATE INDEX IF NOT EXISTS idx_cba_auto_fetch ON customer_bill_account (auto_fetch_enabled) WHERE auto_fetch_enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_cba_auto_pay ON customer_bill_account (auto_pay_enabled) WHERE auto_pay_enabled = TRUE;
