CREATE TABLE ledger_entry (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_account_id   uuid NOT NULL,
    entry_type          varchar(10) NOT NULL,
    amount              numeric(14,2) NOT NULL,
    balance_after       numeric(14,2) NOT NULL,
    reference_type      varchar(30) NOT NULL,
    reference_id        varchar(100),
    idempotency_key     varchar(150),
    description         varchar(255),
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ledger_entry_idempotency_key_key UNIQUE (idempotency_key),
    CONSTRAINT ledger_entry_entry_type_check CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entry_wallet_account_id_fkey FOREIGN KEY (wallet_account_id)
        REFERENCES wallet_account (id) ON DELETE CASCADE
);

CREATE INDEX idx_ledger_wallet_created ON ledger_entry (wallet_account_id, created_at DESC);
CREATE INDEX idx_ledger_reference_id ON ledger_entry (reference_id, created_at DESC);
CREATE INDEX idx_ledger_reference_type_created ON ledger_entry (reference_type, created_at);
