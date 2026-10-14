CREATE TABLE tenant.role (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(50) NOT NULL,
    description character varying(255)
);



ALTER TABLE ONLY tenant.role
    ADD CONSTRAINT role_name_key UNIQUE (name);



ALTER TABLE ONLY tenant.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);




