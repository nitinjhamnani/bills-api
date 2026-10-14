CREATE TABLE platform.biller_service_catalog (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    service_code character varying(30) NOT NULL,
    service_name character varying(100) NOT NULL,
    category character varying(50) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY platform.biller_service_catalog
    ADD CONSTRAINT biller_service_catalog_pkey PRIMARY KEY (id);



ALTER TABLE ONLY platform.biller_service_catalog
    ADD CONSTRAINT biller_service_catalog_service_code_key UNIQUE (service_code);




