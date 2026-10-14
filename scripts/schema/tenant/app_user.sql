CREATE TABLE tenant.app_user (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    phone_number character varying(15) NOT NULL,
    full_name character varying(150) NOT NULL,
    role_id uuid NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    client_id uuid,
    email character varying(150)
);



ALTER TABLE ONLY tenant.app_user
    ADD CONSTRAINT app_user_email_key UNIQUE (email);



ALTER TABLE ONLY tenant.app_user
    ADD CONSTRAINT app_user_phone_number_key UNIQUE (phone_number);



ALTER TABLE ONLY tenant.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.app_user
    ADD CONSTRAINT app_user_client_id_fkey FOREIGN KEY (client_id) REFERENCES tenant.client(id);



ALTER TABLE ONLY tenant.app_user
    ADD CONSTRAINT app_user_role_id_fkey FOREIGN KEY (role_id) REFERENCES tenant.role(id);




