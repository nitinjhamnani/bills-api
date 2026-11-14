CREATE TABLE wallet_transactions (
    transaction_id       uuid PRIMARY KEY,
    wallet_id            uuid NOT NULL REFERENCES wallet (id),
    transaction_type     varchar(10) NOT NULL,
    amount               numeric(14, 2) NOT NULL,
    created_at           timestamptz NOT NULL,
    created_by           varchar(6),
    transaction_category varchar(40) NOT NULL,
    CONSTRAINT wallet_transactions_amount_check CHECK (amount > 0)
);

CREATE INDEX wallet_transactions_wallet_idx ON wallet_transactions (wallet_id, created_at);
CREATE INDEX wallet_transactions_category_idx ON wallet_transactions (transaction_category, created_at);
