CREATE TABLE tenant.client_bill_service_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    bill_service_code character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    enabled boolean DEFAULT true NOT NULL
);



ALTER TABLE ONLY tenant.client_bill_service_config
    ADD CONSTRAINT client_bill_service_config_client_id_bill_service_code_key UNIQUE (client_id, bill_service_code);



ALTER TABLE ONLY tenant.client_bill_service_config
    ADD CONSTRAINT client_bill_service_config_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.client_bill_service_config
    ADD CONSTRAINT client_bill_service_config_client_id_fkey FOREIGN KEY (client_id) REFERENCES tenant.client(id) ON DELETE CASCADE;




