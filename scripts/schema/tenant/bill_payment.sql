CREATE TABLE tenant.bill_payment (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    bill_id uuid NOT NULL,
    client_id uuid NOT NULL,
    amount numeric(14,2) NOT NULL,
    amount_breakdown text,
    bill_number character varying(100),
    bill_date character varying(30),
    due_date character varying(30),
    bill_period character varying(30),
    api_response_snapshot text,
    org_status character varying(20) NOT NULL,
    payment_status character varying(20) NOT NULL,
    bill_status character varying(20) NOT NULL,
    idempotency_key character varying(150) NOT NULL,
    client_paid_at timestamp with time zone,
    client_paid_by character varying(50),
    org_picked_up_at timestamp with time zone,
    org_picked_up_by character varying(50),
    org_paid_at timestamp with time zone,
    org_paid_by character varying(50),
    bbps_transaction_id character varying(100),
    external_transaction_id character varying(100),
    bill_type character varying(50),
    bill_pay_failure_reason character varying(300),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.bill_payment
    ADD CONSTRAINT bill_payment_idempotency_key_key UNIQUE (idempotency_key);



ALTER TABLE ONLY tenant.bill_payment
    ADD CONSTRAINT bill_payment_pkey PRIMARY KEY (id);



CREATE INDEX idx_bill_payment_bill_id ON tenant.bill_payment USING btree (bill_id);



CREATE INDEX idx_bill_payment_client_id ON tenant.bill_payment USING btree (client_id);



CREATE INDEX idx_bill_payment_org_status ON tenant.bill_payment USING btree (org_status);



CREATE INDEX idx_bill_payment_payment_status ON tenant.bill_payment USING btree (payment_status);



ALTER TABLE ONLY tenant.bill_payment
    ADD CONSTRAINT bill_payment_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES tenant.bill(id);




