CREATE TABLE tenant.bill_payment_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    bill_id uuid NOT NULL,
    bill_payment_id uuid,
    event_type character varying(40) NOT NULL,
    description character varying(500),
    actor character varying(50),
    created_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.bill_payment_log
    ADD CONSTRAINT bill_payment_log_pkey PRIMARY KEY (id);



CREATE INDEX idx_bill_payment_log_bill_id ON tenant.bill_payment_log USING btree (bill_id);



ALTER TABLE ONLY tenant.bill_payment_log
    ADD CONSTRAINT bill_payment_log_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES tenant.bill(id);



ALTER TABLE ONLY tenant.bill_payment_log
    ADD CONSTRAINT bill_payment_log_bill_payment_id_fkey FOREIGN KEY (bill_payment_id) REFERENCES tenant.bill_payment(id);




