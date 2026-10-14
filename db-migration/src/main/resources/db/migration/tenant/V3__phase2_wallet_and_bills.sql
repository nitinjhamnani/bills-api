-- Phase 2: agency wallet ledger (funds bill payments) + reward ledger (same engine, different
-- account type - commission credited starting Phase 3), wallet top-up requests, and the bill
-- dual-status lifecycle. See implementation plan sections 5 and 6.

CREATE TABLE IF NOT EXISTS wallet_account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id       UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    wallet_type     VARCHAR(10) NOT NULL CHECK (wallet_type IN ('WALLET', 'REWARD')),
    balance         NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (agency_id, wallet_type)
);

-- One row per movement, referencing the balance immediately after it was applied - a full,
-- append-only audit trail even though (for Phase 2 simplicity) a movement posts against a single
-- wallet_account rather than a full debit/credit pair across two accounts; the balance is always
-- re-derivable by replaying entries in order, which is what matters for reconciliation.
CREATE TABLE IF NOT EXISTS ledger_entry (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_account_id   UUID NOT NULL REFERENCES wallet_account(id) ON DELETE CASCADE,
    entry_type          VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount              NUMERIC(14,2) NOT NULL,
    balance_after       NUMERIC(14,2) NOT NULL,
    reference_type      VARCHAR(30) NOT NULL, -- TOPUP / BILL_PAYMENT / REFUND / COMMISSION / REDEMPTION
    reference_id        VARCHAR(100),
    idempotency_key     VARCHAR(150) UNIQUE,
    description         VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS wallet_topup_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id               UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    amount                  NUMERIC(14,2) NOT NULL,
    method                  VARCHAR(10) NOT NULL CHECK (method IN ('ONLINE', 'OFFLINE')),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/APPROVED/REJECTED/COMPLETED/FAILED
    document_id             UUID REFERENCES document(id),
    provider_reference_id   VARCHAR(100),
    idempotency_key         VARCHAR(150) UNIQUE NOT NULL,
    requested_by            VARCHAR(100),
    reviewed_by             VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bill (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id                   UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    bill_service_code           VARCHAR(30) NOT NULL,
    customer_bill_account_no    VARCHAR(100) NOT NULL,
    customer_name               VARCHAR(150),
    amount                      NUMERIC(14,2) NOT NULL,
    idempotency_key             VARCHAR(150) UNIQUE NOT NULL,
    wallet_payment_status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/PAID/REFUNDED
    bill_settlement_status      VARCHAR(20) NOT NULL DEFAULT 'QUEUED',  -- see implementation plan section 5
    submitted_by                VARCHAR(100),
    picked_up_by                VARCHAR(100),
    approved_by                 VARCHAR(100),
    provider_reference_id       VARCHAR(100),
    failure_reason              VARCHAR(500),
    submitted_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bill_settlement_status ON bill (bill_settlement_status);
CREATE INDEX IF NOT EXISTS idx_bill_agency ON bill (agency_id);
