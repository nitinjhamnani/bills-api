CREATE TABLE bill_dispute (
    id               uuid PRIMARY KEY,
    bill_payment_id  uuid NOT NULL,
    raised_by        varchar(255),
    reason           varchar(255) NOT NULL,
    status           varchar(255) NOT NULL,
    resolution_notes varchar(255),
    resolved_by      varchar(255),
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL
);

CREATE INDEX bill_dispute_payment_idx ON bill_dispute (bill_payment_id);
CREATE INDEX bill_dispute_status_idx ON bill_dispute (status, created_at DESC);
