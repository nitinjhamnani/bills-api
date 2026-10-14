CREATE TABLE platform.user_directory (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    varchar(15) NOT NULL,
    user_id         uuid NOT NULL,
    tenant_schema   varchar(63),
    role            varchar(50) NOT NULL,
    display_name    varchar(150),
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    client_id       uuid,
    email           varchar(150),
    password_hash   varchar(255),
    CONSTRAINT user_directory_phone_number_key UNIQUE (phone_number),
    CONSTRAINT user_directory_email_key UNIQUE (email)
);

CREATE INDEX idx_user_directory_tenant_schema
    ON platform.user_directory (tenant_schema, created_at);
