CREATE TABLE client (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name     varchar(200) NOT NULL,
    contact_phone   varchar(15),
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    client_type     varchar(20) NOT NULL DEFAULT 'CORPORATE',
    client_code     varchar(8),
    CONSTRAINT client_client_code_key UNIQUE (client_code),
    CONSTRAINT client_client_type_check CHECK (client_type IN ('CORPORATE', 'PARTNER'))
);
