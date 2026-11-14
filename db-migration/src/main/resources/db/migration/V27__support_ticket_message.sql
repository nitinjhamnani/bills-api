CREATE TABLE support_ticket_message (
    id             uuid PRIMARY KEY,
    ticket_id      uuid NOT NULL REFERENCES support_ticket (id),
    author_side    varchar(255) NOT NULL,
    author_user_id varchar(6),
    author_name    varchar(255) NOT NULL,
    body           text NOT NULL,
    created_at     timestamptz NOT NULL
);

CREATE INDEX support_ticket_message_ticket_idx ON support_ticket_message (ticket_id, created_at);
