CREATE TABLE app_user (
    user_id         varchar(6) PRIMARY KEY,
    name            varchar(150) NOT NULL,
    email           varchar(150),
    phone           varchar(15) NOT NULL,
    created_by_type varchar(32),
    created_by_id   varchar(6),
    created_on      timestamptz NOT NULL,
    is_active       boolean NOT NULL DEFAULT true,
    user_type       varchar(32) NOT NULL,
    user_org_id     varchar(64) NOT NULL,
    password        varchar NOT NULL,
    CONSTRAINT app_user_id_check CHECK (user_id ~ '^[A-Z0-9]{6}$'),
    CONSTRAINT app_user_name_check CHECK (char_length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX app_user_phone_idx ON app_user (phone);
CREATE UNIQUE INDEX app_user_email_idx ON app_user (email) WHERE email IS NOT NULL;
CREATE INDEX app_user_org_idx ON app_user (user_org_id);
CREATE INDEX app_user_type_idx ON app_user (user_type);
