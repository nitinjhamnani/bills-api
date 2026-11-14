CREATE TABLE platform_transaction (
    transaction_id       uuid PRIMARY KEY,
    wallet_id            uuid NOT NULL REFERENCES wallet (id),
    transaction_type     varchar(10) NOT NULL,
    amount               numeric(14, 2) NOT NULL,
    created_at           timestamptz NOT NULL,
    created_by           varchar(6),
    transaction_category varchar(40) NOT NULL,
    CONSTRAINT platform_transaction_amount_check CHECK (amount > 0)
);

CREATE INDEX platform_transaction_wallet_idx ON platform_transaction (wallet_id, created_at DESC);
CREATE INDEX platform_transaction_category_idx ON platform_transaction (transaction_category, created_at);
