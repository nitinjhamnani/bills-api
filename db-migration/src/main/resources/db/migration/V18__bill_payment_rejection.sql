CREATE TABLE bill_payment_rejection (
    id               uuid PRIMARY KEY,
    bill_payment_id  uuid NOT NULL,
    vendor_client_id varchar(255) NOT NULL,
    reason           varchar(300) NOT NULL,
    rejected_by      varchar(50) NOT NULL,
    created_at       timestamptz NOT NULL,
    CONSTRAINT bill_payment_rejection_reason_check CHECK (char_length(btrim(reason)) > 0)
);

CREATE INDEX bill_payment_rejection_payment_idx ON bill_payment_rejection (bill_payment_id);
CREATE INDEX bill_payment_rejection_vendor_idx ON bill_payment_rejection (vendor_client_id);
