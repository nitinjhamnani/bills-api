CREATE TABLE tenant.audit_log (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_user_id character varying(100),
    actor_role character varying(50),
    action character varying(100) NOT NULL,
    entity_type character varying(100),
    entity_id character varying(100),
    details text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY tenant.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);




