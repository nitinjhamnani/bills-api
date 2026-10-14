-- Mirrors platform.user_directory's new email column (see platform V4) so a tenant's own user
-- listings can display it. Password hashes live only in platform.user_directory - that's the
-- one table login actually authenticates against.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS email VARCHAR(150);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'app_user_email_key'
    ) THEN
        ALTER TABLE app_user ADD CONSTRAINT app_user_email_key UNIQUE (email);
    END IF;
END $$;
