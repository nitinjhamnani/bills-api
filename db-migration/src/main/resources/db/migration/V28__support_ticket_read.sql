CREATE TABLE support_ticket_read (
    id           uuid PRIMARY KEY,
    ticket_id    uuid NOT NULL REFERENCES support_ticket (id),
    user_id      varchar(6) NOT NULL REFERENCES app_user (user_id),
    last_read_at timestamptz NOT NULL,
    CONSTRAINT support_ticket_read_ticket_user_key UNIQUE (ticket_id, user_id)
);
