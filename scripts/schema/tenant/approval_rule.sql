CREATE TABLE tenant.approval_rule (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    action_type character varying(40) NOT NULL,
    min_amount numeric(14,2) DEFAULT 0 NOT NULL,
    required_approver_count integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);



ALTER TABLE ONLY tenant.approval_rule
    ADD CONSTRAINT approval_rule_action_type_key UNIQUE (action_type);



ALTER TABLE ONLY tenant.approval_rule
    ADD CONSTRAINT approval_rule_pkey PRIMARY KEY (id);




