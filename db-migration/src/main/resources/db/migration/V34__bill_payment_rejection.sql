CREATE TABLE bill_payment_rejection (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_payment_id uuid NOT NULL REFERENCES bill_payment (id),
    vendor_client_id uuid NOT NULL REFERENCES client (id),
    reason varchar(300) NOT NULL,
    rejected_by varchar(50) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bill_payment_rejection_reason_check CHECK (char_length(btrim(reason)) > 0),
    CONSTRAINT bill_payment_rejection_vendor_unique UNIQUE (bill_payment_id, vendor_client_id)
);
