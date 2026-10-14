CREATE TABLE tenant.ledger_entry (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    wallet_account_id uuid NOT NULL,
    entry_type character varying(10) NOT NULL,
    amount numeric(14,2) NOT NULL,
    balance_after numeric(14,2) NOT NULL,
    reference_type character varying(30) NOT NULL,
    reference_id character varying(100),
    idempotency_key character varying(150),
    description character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ledger_entry_entry_type_check CHECK (((entry_type)::text = ANY ((ARRAY['DEBIT'::character varying, 'CREDIT'::character varying])::text[])))
);



ALTER TABLE ONLY tenant.ledger_entry
    ADD CONSTRAINT ledger_entry_idempotency_key_key UNIQUE (idempotency_key);



ALTER TABLE ONLY tenant.ledger_entry
    ADD CONSTRAINT ledger_entry_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.ledger_entry
    ADD CONSTRAINT ledger_entry_wallet_account_id_fkey FOREIGN KEY (wallet_account_id) REFERENCES tenant.wallet_account(id) ON DELETE CASCADE;




