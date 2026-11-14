CREATE TABLE support_ticket (
    id               uuid PRIMARY KEY,
    client_id        varchar(8) NOT NULL,
    subject          varchar(255) NOT NULL,
    description      text NOT NULL,
    status           varchar(255) NOT NULL,
    raised_by        varchar(255),
    picked_up_by     varchar(255),
    resolved_by      varchar(255),
    resolution_notes varchar(255),
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL
);

CREATE INDEX support_ticket_client_idx ON support_ticket (client_id, status, created_at DESC);
CREATE INDEX support_ticket_status_idx ON support_ticket (status, created_at DESC);
