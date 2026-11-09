CREATE TABLE permission (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            varchar(100) NOT NULL,
    description     varchar(255),
    CONSTRAINT permission_code_key UNIQUE (code)
);
