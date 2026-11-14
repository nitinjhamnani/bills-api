CREATE TABLE wallet (
    id               uuid PRIMARY KEY,
    balance          numeric(14, 2) NOT NULL DEFAULT 0,
    is_active        boolean NOT NULL DEFAULT true,
    wallet_user_type varchar(8) NOT NULL,
    wallet_user_id   varchar(8) NOT NULL,
    last_updated_at  timestamptz NOT NULL,
    CONSTRAINT wallet_owner_key UNIQUE (wallet_user_type, wallet_user_id),
    CONSTRAINT wallet_balance_check CHECK (balance >= 0)
);

CREATE INDEX wallet_user_idx ON wallet (wallet_user_id);
