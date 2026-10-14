CREATE TABLE tenant.document (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    owner_type character varying(40) NOT NULL,
    owner_id character varying(100),
    file_name character varying(255) NOT NULL,
    content_type character varying(100),
    storage_path character varying(500) NOT NULL,
    status character varying(20) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    uploaded_by character varying(100),
    reviewed_by character varying(100),
    review_notes character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY tenant.document
    ADD CONSTRAINT document_pkey PRIMARY KEY (id);




