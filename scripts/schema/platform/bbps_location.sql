CREATE TABLE platform.bbps_location (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    provider_code character varying(32) NOT NULL,
    location_id character varying(64) NOT NULL,
    name character varying(200) NOT NULL,
    abbreviation character varying(16),
    active boolean DEFAULT true NOT NULL,
    synced_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE ONLY platform.bbps_location
    ADD CONSTRAINT bbps_location_pkey PRIMARY KEY (id);

ALTER TABLE ONLY platform.bbps_location
    ADD CONSTRAINT uq_bbps_location UNIQUE (provider_code, location_id);
