INSERT INTO role (name, description) VALUES
    ('SALES', 'Read-only progress for the corporates this person brought in.')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE client ADD COLUMN sales_user_id uuid REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX client_sales_user_id_idx ON client (sales_user_id);
