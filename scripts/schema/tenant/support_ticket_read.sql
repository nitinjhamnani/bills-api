CREATE TABLE tenant.support_ticket_read (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    ticket_id uuid NOT NULL,
    user_id uuid NOT NULL,
    last_read_at timestamp with time zone NOT NULL
);



ALTER TABLE ONLY tenant.support_ticket_read
    ADD CONSTRAINT support_ticket_read_pkey PRIMARY KEY (id);



ALTER TABLE ONLY tenant.support_ticket_read
    ADD CONSTRAINT support_ticket_read_ticket_id_user_id_key UNIQUE (ticket_id, user_id);



CREATE INDEX idx_support_ticket_read_user ON tenant.support_ticket_read USING btree (user_id);



ALTER TABLE ONLY tenant.support_ticket_read
    ADD CONSTRAINT support_ticket_read_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES tenant.support_ticket(id);




