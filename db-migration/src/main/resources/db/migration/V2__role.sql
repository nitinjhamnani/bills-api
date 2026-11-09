CREATE TABLE role (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            varchar(50) NOT NULL,
    description     varchar(255),
    CONSTRAINT role_name_key UNIQUE (name)
);

INSERT INTO role (name, description) VALUES
    ('ORGANISATION_ADMIN', 'Full control over the organisation, clients, users and reports'),
    ('MAKER', 'Can initiate bill payments and other money-moving actions'),
    ('CHECKER', 'Approves maker-initiated financial actions'),
    ('VIEWER', 'Read-only access to organisation data'),
    ('CLIENT_ADMIN', 'Manages a client: wallet, bills and users'),
    ('CLIENT_OPERATOR', 'Day-to-day client operations under a client admin')
ON CONFLICT (name) DO NOTHING;
