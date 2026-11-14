CREATE TABLE bill_fetch (
    bill_fetch_id       uuid PRIMARY KEY,
    bill_id             varchar(10) NOT NULL REFERENCES bills (bill_id),
    bill_amount         numeric(14, 2),
    bill_cycle          varchar(40),
    bill_due_date       date,
    bill_generated_date date,
    bill_consumer_name  varchar(200),
    bill_status         varchar(16) NOT NULL,
    bill_payment_status varchar(16) NOT NULL,
    fetched_on          timestamptz NOT NULL,
    fetch_status        varchar(16) NOT NULL,
    fetch_remarks       varchar(500),
    fetch_provider      varchar(32) NOT NULL,
    fetch_snapshot_json jsonb
);

CREATE INDEX bill_fetch_bill_idx ON bill_fetch (bill_id, fetched_on DESC);
CREATE INDEX bill_fetch_status_idx ON bill_fetch (fetch_status, bill_status);
