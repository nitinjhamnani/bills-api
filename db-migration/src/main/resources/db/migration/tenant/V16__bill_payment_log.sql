CREATE TABLE bill_payment_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id         uuid NOT NULL,
    bill_payment_id uuid,
    event_type      varchar(40) NOT NULL,
    description     varchar(500),
    actor           varchar(50),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bill_payment_log_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES bill (id),
    CONSTRAINT bill_payment_log_bill_payment_id_fkey FOREIGN KEY (bill_payment_id)
        REFERENCES bill_payment (id)
);

CREATE INDEX idx_bill_payment_log_bill_created
    ON bill_payment_log (bill_id, created_at DESC);
CREATE INDEX idx_bill_payment_log_payment_created
    ON bill_payment_log (bill_payment_id, created_at DESC);
