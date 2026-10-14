CREATE TABLE support_ticket_read (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       uuid NOT NULL,
    user_id         uuid NOT NULL,
    last_read_at    timestamptz NOT NULL,
    CONSTRAINT support_ticket_read_ticket_id_user_id_key UNIQUE (ticket_id, user_id),
    CONSTRAINT support_ticket_read_ticket_id_fkey FOREIGN KEY (ticket_id)
        REFERENCES support_ticket (id)
);

CREATE INDEX idx_support_ticket_read_user ON support_ticket_read (user_id);
