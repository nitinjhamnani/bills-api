CREATE TABLE platform.bbps_category (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    provider_code character varying(32) NOT NULL,
    category_id character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    category_group character varying(32),
    active boolean DEFAULT true NOT NULL,
    synced_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.bbps_category
    ADD CONSTRAINT bbps_category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.bbps_category
    ADD CONSTRAINT uq_bbps_category UNIQUE (provider_code, category_id);
