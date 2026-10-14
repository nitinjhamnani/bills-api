CREATE TABLE platform.organisation (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organisation_code character varying(30) NOT NULL,
    organisation_name character varying(200) NOT NULL,
    tenant_schema character varying(63) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY platform.organisation
    ADD CONSTRAINT company_company_code_key UNIQUE (organisation_code);



ALTER TABLE ONLY platform.organisation
    ADD CONSTRAINT company_pkey PRIMARY KEY (id);



ALTER TABLE ONLY platform.organisation
    ADD CONSTRAINT company_tenant_schema_key UNIQUE (tenant_schema);




