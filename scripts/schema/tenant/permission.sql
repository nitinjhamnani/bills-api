CREATE TABLE tenant.permission (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    code character varying(100) NOT NULL,
    description character varying(255)
);



ALTER TABLE ONLY tenant.permission
    ADD CONSTRAINT permission_code_key UNIQUE (code);



ALTER TABLE ONLY tenant.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);




