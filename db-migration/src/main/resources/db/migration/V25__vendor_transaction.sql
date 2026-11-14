CREATE TABLE vendor_transaction (
    transaction_id       uuid PRIMARY KEY,
    vendor_id            varchar(8) NOT NULL REFERENCES vendor_details (vendor_id),
    transaction_type     varchar(10) NOT NULL,
    amount               numeric(14, 2) NOT NULL,
    created_at           timestamptz NOT NULL,
    created_by           varchar(6),
    transaction_category varchar(16) NOT NULL,
    CONSTRAINT vendor_transaction_amount_check CHECK (amount > 0)
);

CREATE INDEX vendor_transaction_vendor_idx ON vendor_transaction (vendor_id, created_at DESC);
CREATE INDEX vendor_transaction_category_idx ON vendor_transaction (transaction_category, created_at);
