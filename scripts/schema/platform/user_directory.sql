CREATE TABLE platform.user_directory (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    phone_number character varying(15) NOT NULL,
    user_id uuid NOT NULL,
    tenant_schema character varying(63),
    role character varying(50) NOT NULL,
    display_name character varying(150),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    client_id uuid,
    email character varying(150),
    password_hash character varying(255)
);



ALTER TABLE ONLY platform.user_directory
    ADD CONSTRAINT user_directory_email_key UNIQUE (email);



ALTER TABLE ONLY platform.user_directory
    ADD CONSTRAINT user_directory_phone_number_key UNIQUE (phone_number);



ALTER TABLE ONLY platform.user_directory
    ADD CONSTRAINT user_directory_pkey PRIMARY KEY (id);




