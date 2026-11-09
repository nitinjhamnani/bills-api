DELETE FROM bill_dispute;
DELETE FROM bill_payment_rejection;
DELETE FROM bill_payment_log;
DELETE FROM bill_payment;
DELETE FROM bill;

INSERT INTO role (name, description) VALUES
    ('BRANCH_USER', 'Works inside one corporate branch: registers bills and watches fetched cycles.')
ON CONFLICT (name) DO NOTHING;

CREATE TABLE branch (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   uuid NOT NULL REFERENCES client (id),
    name        varchar(150) NOT NULL,
    address     varchar(500),
    code        varchar(40),
    comment     varchar(500),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT branch_name_check CHECK (char_length(btrim(name)) > 0)
);

CREATE INDEX branch_client_idx ON branch (client_id);
CREATE UNIQUE INDEX branch_client_code_uidx ON branch (client_id, code) WHERE code IS NOT NULL;

ALTER TABLE app_user ADD COLUMN branch_id uuid REFERENCES branch (id);
CREATE INDEX app_user_branch_idx ON app_user (branch_id);

ALTER TABLE user_directory ADD COLUMN branch_id uuid;

ALTER TABLE bill ADD COLUMN branch_id uuid NOT NULL REFERENCES branch (id);
CREATE INDEX bill_branch_idx ON bill (branch_id);
