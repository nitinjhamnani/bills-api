CREATE TABLE bills (
    bill_id              varchar(10) PRIMARY KEY,
    operator_name        varchar(300) NOT NULL,
    source_type          varchar(16) NOT NULL,
    source_id            varchar(8) NOT NULL,
    branch_id            varchar(8) NOT NULL REFERENCES branch (branch_id),
    user_id              varchar(6) NOT NULL REFERENCES app_user (user_id),
    category_name        varchar(200) NOT NULL,
    category_id          uuid NOT NULL,
    primary_param_name   varchar(200) NOT NULL,
    primary_param_value  varchar(100) NOT NULL,
    input_param_json     jsonb NOT NULL,
    customer_mobile      varchar(10),
    latest_bill_fetch_id uuid,
    CONSTRAINT bills_id_check CHECK (bill_id ~ '^[A-Z0-9]{10}$'),
    CONSTRAINT bills_mobile_check CHECK (customer_mobile IS NULL OR customer_mobile ~ '^[6-9][0-9]{9}$')
);

CREATE INDEX bills_source_idx ON bills (source_type, source_id);
CREATE INDEX bills_branch_idx ON bills (branch_id);
CREATE INDEX bills_account_idx ON bills (source_type, source_id, operator_name, primary_param_value);
