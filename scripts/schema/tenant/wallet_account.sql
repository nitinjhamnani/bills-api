CREATE TABLE tenant.wallet_account (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    wallet_type character varying(10) NOT NULL,
    balance numeric(14,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT wallet_account_wallet_type_check CHECK (((wallet_type)::text = 'WALLET'::text))
);



ALTER TABLE ONLY tenant.wallet_account
    ADD CONSTRAINT wallet_account_client_id_wallet_type_key UNIQUE (client_id, wallet_type);



ALTER TABLE ONLY tenant.wallet_account
    ADD CONSTRAINT wallet_account_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.wallet_account
    ADD CONSTRAINT wallet_account_client_id_fkey FOREIGN KEY (client_id) REFERENCES tenant.client(id) ON DELETE CASCADE;




