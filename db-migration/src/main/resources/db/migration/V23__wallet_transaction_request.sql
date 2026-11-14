CREATE TABLE wallet_transaction_request (
    request_id     uuid PRIMARY KEY,
    corp_id        varchar(8) NOT NULL REFERENCES corp_details (corp_id),
    amount         numeric(14, 2) NOT NULL,
    mode           varchar(10) NOT NULL,
    utr            varchar(150) NOT NULL,
    created_date   timestamptz NOT NULL,
    created_by     varchar(150),
    created_by_id  varchar(6),
    proof          varchar(500) NOT NULL,
    approved_by    varchar(150),
    approved_by_id varchar(6),
    approved_on    timestamptz,
    status         varchar(16) NOT NULL,
    last_updated   timestamptz NOT NULL,
    CONSTRAINT wallet_transaction_request_amount_check CHECK (amount > 0)
);

CREATE INDEX wallet_transaction_request_corp_idx ON wallet_transaction_request (corp_id, created_date DESC);
CREATE INDEX wallet_transaction_request_status_idx ON wallet_transaction_request (status, created_date);
