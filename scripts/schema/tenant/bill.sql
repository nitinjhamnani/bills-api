CREATE TABLE tenant.bill (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    bill_service_code character varying(60) NOT NULL,
    biller_id character varying(60),
    biller_name character varying(150),
    customer_bill_account_no character varying(100) NOT NULL,
    customer_name character varying(150),
    customer_mobile_number character varying(20),
    amount numeric(14,2),
    amount_breakdown text,
    bill_number character varying(100),
    bill_date character varying(30),
    due_date character varying(30),
    bill_period character varying(30),
    payment_status character varying(20) NOT NULL,
    bill_status character varying(20) NOT NULL,
    last_fetch_response_snapshot text,
    last_fetch_at timestamp with time zone,
    last_fetch_failed boolean DEFAULT false NOT NULL,
    last_fetch_failure_reason character varying(300),
    auto_fetch_enabled boolean DEFAULT true NOT NULL,
    auto_pay_enabled boolean DEFAULT false NOT NULL,
    last_paid_due_date character varying(30),
    created_by character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.bill
    ADD CONSTRAINT bill_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.bill
    ADD CONSTRAINT uq_bill_client_service_account UNIQUE (client_id, bill_service_code, customer_bill_account_no);




