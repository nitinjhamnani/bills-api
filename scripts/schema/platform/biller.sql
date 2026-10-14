CREATE TABLE platform.biller (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    biller_id character varying(50) NOT NULL,
    biller_name character varying(200) NOT NULL,
    category character varying(30) NOT NULL,
    state character varying(50),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY platform.biller
    ADD CONSTRAINT biller_biller_id_key UNIQUE (biller_id);



ALTER TABLE ONLY platform.biller
    ADD CONSTRAINT biller_pkey PRIMARY KEY (id);



CREATE INDEX idx_biller_category ON platform.biller USING btree (category);



ALTER TABLE ONLY platform.biller
    ADD CONSTRAINT biller_category_fkey FOREIGN KEY (category) REFERENCES platform.biller_service_catalog(service_code);




