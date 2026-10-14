CREATE TABLE tenant.client (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_name character varying(200) NOT NULL,
    contact_phone character varying(15),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    client_type character varying(20) DEFAULT 'CORPORATE'::character varying NOT NULL,
    client_code character varying(6),
    CONSTRAINT client_client_type_check CHECK (((client_type)::text = ANY ((ARRAY['CORPORATE'::character varying, 'PARTNER'::character varying])::text[])))
);



ALTER TABLE ONLY tenant.client
    ADD CONSTRAINT client_client_code_key UNIQUE (client_code);



ALTER TABLE ONLY tenant.client
    ADD CONSTRAINT client_pkey PRIMARY KEY (id);




