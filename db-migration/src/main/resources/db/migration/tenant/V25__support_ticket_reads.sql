CREATE TABLE support_ticket_read (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id     UUID NOT NULL REFERENCES support_ticket(id),
    user_id       UUID NOT NULL,
    last_read_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (ticket_id, user_id)
);

CREATE INDEX idx_support_ticket_read_user ON support_ticket_read(user_id);
