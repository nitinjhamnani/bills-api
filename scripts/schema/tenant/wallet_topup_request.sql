CREATE TABLE tenant.wallet_topup_request (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    amount numeric(14,2) NOT NULL,
    payment_mode character varying(10) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    document_id uuid,
    idempotency_key character varying(150) NOT NULL,
    requested_by character varying(100),
    reviewed_by character varying(100),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    payment_reference character varying(150),
    CONSTRAINT wallet_topup_request_payment_mode_check CHECK (((payment_mode)::text = ANY ((ARRAY['RTGS'::character varying, 'NEFT'::character varying, 'UPI'::character varying, 'CASH'::character varying, 'OTHER'::character varying])::text[])))
);



ALTER TABLE ONLY tenant.wallet_topup_request
    ADD CONSTRAINT wallet_topup_request_idempotency_key_key UNIQUE (idempotency_key);



ALTER TABLE ONLY tenant.wallet_topup_request
    ADD CONSTRAINT wallet_topup_request_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.wallet_topup_request
    ADD CONSTRAINT wallet_topup_request_client_id_fkey FOREIGN KEY (client_id) REFERENCES tenant.client(id) ON DELETE CASCADE;



ALTER TABLE ONLY tenant.wallet_topup_request
    ADD CONSTRAINT wallet_topup_request_document_id_fkey FOREIGN KEY (document_id) REFERENCES tenant.document(id);




