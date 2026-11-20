ALTER TABLE wallet_transaction_request
    ADD COLUMN IF NOT EXISTS remarks varchar(500);
