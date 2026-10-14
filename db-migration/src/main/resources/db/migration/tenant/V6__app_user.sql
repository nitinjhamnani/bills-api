CREATE TABLE app_user (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    varchar(15) NOT NULL,
    full_name       varchar(150) NOT NULL,
    role_id         uuid NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    client_id       uuid,
    email           varchar(150),
    CONSTRAINT app_user_phone_number_key UNIQUE (phone_number),
    CONSTRAINT app_user_email_key UNIQUE (email),
    CONSTRAINT app_user_role_id_fkey FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT app_user_client_id_fkey FOREIGN KEY (client_id) REFERENCES client (id)
);

CREATE INDEX idx_app_user_client_id ON app_user (client_id);
