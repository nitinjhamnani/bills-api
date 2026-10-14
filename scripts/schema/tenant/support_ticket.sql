CREATE TABLE tenant.support_ticket (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    client_id uuid NOT NULL,
    subject character varying(200) NOT NULL,
    description text NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying NOT NULL,
    raised_by character varying(50),
    picked_up_by character varying(50),
    resolved_by character varying(50),
    resolution_notes character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.support_ticket
    ADD CONSTRAINT support_ticket_pkey PRIMARY KEY (id);



CREATE INDEX idx_support_ticket_client_id ON tenant.support_ticket USING btree (client_id);




