CREATE TABLE bill_payment (
    id                           uuid PRIMARY KEY,
    bill_fetch_id                uuid NOT NULL REFERENCES bill_fetch (bill_fetch_id),
    bill_amount                  numeric(14, 2) NOT NULL,
    corp_payment_on              timestamptz NOT NULL,
    corp_id                      varchar(8) NOT NULL REFERENCES corp_details (corp_id),
    corp_name                    varchar(200) NOT NULL,
    corp_paid_user_id            varchar(6),
    corp_payment_status          varchar(16) NOT NULL,
    bill_assigned_to             varchar(16) NOT NULL,
    bill_assigned_to_id          varchar(8) NOT NULL,
    bill_assigned_to_name        varchar(200) NOT NULL,
    bill_assigned_to_status      varchar(16) NOT NULL,
    bill_assigned_on             timestamptz NOT NULL,
    bill_paid_on                 timestamptz,
    transaction_reference_number varchar(120),
    bbps_external_reference      varchar(120),
    payment_proof                varchar(500),
    verified_by                  varchar(150),
    verified_by_id               varchar(6),
    verified_on                  timestamptz,
    is_verified                  boolean NOT NULL DEFAULT false,
    CONSTRAINT bill_payment_amount_check CHECK (bill_amount > 0)
);

CREATE INDEX bill_payment_corp_idx ON bill_payment (corp_id, corp_payment_on DESC);
CREATE INDEX bill_payment_assigned_idx ON bill_payment (bill_assigned_to, bill_assigned_to_id, bill_assigned_to_status);
CREATE INDEX bill_payment_fetch_idx ON bill_payment (bill_fetch_id);
