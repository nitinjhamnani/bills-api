CREATE TABLE tenant.bill_dispute (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    bill_payment_id uuid NOT NULL,
    raised_by character varying(50),
    reason character varying(500) NOT NULL,
    status character varying(20) DEFAULT 'RAISED'::character varying NOT NULL,
    resolution_notes character varying(500),
    resolved_by character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.bill_dispute
    ADD CONSTRAINT bill_dispute_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.bill_dispute
    ADD CONSTRAINT bill_dispute_bill_payment_id_fkey FOREIGN KEY (bill_payment_id) REFERENCES tenant.bill_payment(id);




