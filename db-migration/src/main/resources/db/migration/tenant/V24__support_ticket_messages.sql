CREATE TABLE support_ticket_message (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID NOT NULL REFERENCES support_ticket(id),
    author_side     VARCHAR(20) NOT NULL,
    author_user_id  UUID,
    author_name     VARCHAR(120) NOT NULL,
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_support_ticket_message_ticket ON support_ticket_message(ticket_id, created_at);

INSERT INTO support_ticket_message (id, ticket_id, author_side, author_user_id, author_name, body, created_at)
SELECT gen_random_uuid(),
       id,
       'CLIENT',
       NULL,
       COALESCE(NULLIF(btrim(raised_by), ''), 'Client'),
       description,
       created_at
FROM support_ticket
WHERE description IS NOT NULL AND btrim(description) <> '';

INSERT INTO support_ticket_message (id, ticket_id, author_side, author_user_id, author_name, body, created_at)
SELECT gen_random_uuid(),
       id,
       'ORGANISATION',
       NULL,
       COALESCE(NULLIF(btrim(resolved_by), ''), 'Organisation'),
       resolution_notes,
       updated_at
FROM support_ticket
WHERE resolution_notes IS NOT NULL AND btrim(resolution_notes) <> '';
