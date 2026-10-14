CREATE TABLE support_ticket (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           uuid NOT NULL,
    subject             varchar(200) NOT NULL,
    description         text NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'OPEN',
    raised_by           varchar(50),
    picked_up_by        varchar(50),
    resolved_by         varchar(50),
    resolution_notes    varchar(1000),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_ticket_client_created
    ON support_ticket (client_id, created_at DESC);
