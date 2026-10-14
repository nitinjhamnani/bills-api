CREATE TABLE bill_dispute (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_payment_id     uuid NOT NULL,
    raised_by           varchar(50),
    reason              varchar(500) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'RAISED',
    resolution_notes    varchar(500),
    resolved_by         varchar(50),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bill_dispute_bill_payment_id_fkey FOREIGN KEY (bill_payment_id)
        REFERENCES bill_payment (id)
);

CREATE INDEX idx_bill_dispute_payment ON bill_dispute (bill_payment_id);
