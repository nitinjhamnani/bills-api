CREATE TABLE support_ticket_message (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       uuid NOT NULL,
    author_side     varchar(20) NOT NULL,
    author_user_id  uuid,
    author_name     varchar(120) NOT NULL,
    body            text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT support_ticket_message_ticket_id_fkey FOREIGN KEY (ticket_id)
        REFERENCES support_ticket (id)
);

CREATE INDEX idx_support_ticket_message_ticket
    ON support_ticket_message (ticket_id, created_at);
