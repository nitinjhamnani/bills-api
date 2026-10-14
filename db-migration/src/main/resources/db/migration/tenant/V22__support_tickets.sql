CREATE TABLE support_ticket (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           UUID NOT NULL,
    subject             VARCHAR(200) NOT NULL,
    description         TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    raised_by           VARCHAR(50),
    picked_up_by        VARCHAR(50),
    resolved_by         VARCHAR(50),
    resolution_notes    VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_support_ticket_client_id ON support_ticket(client_id);
