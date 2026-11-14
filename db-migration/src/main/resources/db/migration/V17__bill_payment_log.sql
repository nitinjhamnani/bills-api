CREATE TABLE bill_payment_log (
    id              uuid PRIMARY KEY,
    bill_id         varchar(10) NOT NULL,
    bill_payment_id uuid,
    event_type      varchar(255) NOT NULL,
    description     varchar(255),
    actor           varchar(255),
    created_at      timestamptz NOT NULL
);

CREATE INDEX bill_payment_log_bill_idx ON bill_payment_log (bill_id, created_at DESC);
