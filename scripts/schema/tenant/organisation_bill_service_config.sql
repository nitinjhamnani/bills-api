CREATE TABLE tenant.organisation_bill_service_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    bill_service_code character varying(30) NOT NULL,
    bill_service_name character varying(100) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY tenant.organisation_bill_service_config
    ADD CONSTRAINT organisation_bill_service_config_bill_service_code_key UNIQUE (bill_service_code);



ALTER TABLE ONLY tenant.organisation_bill_service_config
    ADD CONSTRAINT organisation_bill_service_config_pkey PRIMARY KEY (id);




