CREATE TABLE vendor_payments (
    id                      uuid PRIMARY KEY,
    bill_payment_id         uuid NOT NULL,
    bill_amount             numeric(14, 2) NOT NULL,
    bill_paid_on            timestamptz NOT NULL,
    commission_percent      numeric(6, 3),
    commission_status       varchar(16) NOT NULL,
    commission_reference_id varchar(120),
    commission_mode         varchar(40),
    commission_received     boolean NOT NULL DEFAULT false,
    payment_mode            varchar(40),
    payment_status          varchar(16) NOT NULL,
    payment_made_on         timestamptz,
    payment_made_by         varchar(16),
    payment_made_by_id      varchar(6),
    payment_reference_id    varchar(120),
    CONSTRAINT vendor_payments_bill_payment_key UNIQUE (bill_payment_id),
    CONSTRAINT vendor_payments_amount_check CHECK (bill_amount > 0)
);

CREATE INDEX vendor_payments_payment_status_idx ON vendor_payments (payment_status);
CREATE INDEX vendor_payments_commission_status_idx ON vendor_payments (commission_status);
