CREATE TABLE wallet_account (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       uuid NOT NULL,
    wallet_type     varchar(10) NOT NULL,
    balance         numeric(14,2) NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT wallet_account_client_id_wallet_type_key UNIQUE (client_id, wallet_type),
    CONSTRAINT wallet_account_wallet_type_check CHECK (wallet_type = 'WALLET'),
    CONSTRAINT wallet_account_client_id_fkey FOREIGN KEY (client_id)
        REFERENCES client (id) ON DELETE CASCADE
);

CREATE INDEX idx_wallet_account_type ON wallet_account (wallet_type);
