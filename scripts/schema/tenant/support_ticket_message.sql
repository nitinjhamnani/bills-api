CREATE TABLE tenant.support_ticket_message (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    ticket_id uuid NOT NULL,
    author_side character varying(20) NOT NULL,
    author_user_id uuid,
    author_name character varying(120) NOT NULL,
    body text NOT NULL,
    created_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.support_ticket_message
    ADD CONSTRAINT support_ticket_message_pkey PRIMARY KEY (id);



CREATE INDEX idx_support_ticket_message_ticket ON tenant.support_ticket_message USING btree (ticket_id, created_at);



ALTER TABLE ONLY tenant.support_ticket_message
    ADD CONSTRAINT support_ticket_message_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES tenant.support_ticket(id);




