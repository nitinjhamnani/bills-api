CREATE TABLE tenant.fee_config (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    bill_service_code character varying(30),
    fee_type character varying(10) NOT NULL,
    value numeric(10,4) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT fee_config_fee_type_check CHECK (((fee_type)::text = ANY ((ARRAY['FLAT'::character varying, 'PERCENTAGE'::character varying])::text[])))
);



ALTER TABLE ONLY tenant.fee_config
    ADD CONSTRAINT fee_config_pkey PRIMARY KEY (id);




