-- Adds optional password-based login alongside phone+OTP (implementation plan &sect;8.1 extension):
-- an agency admin can be onboarded with an email + password, then log in with either phone/email +
-- password, or phone + OTP as before. Both columns stay NULL for users who never get a password.
ALTER TABLE user_directory ADD COLUMN IF NOT EXISTS email VARCHAR(150);
ALTER TABLE user_directory ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'user_directory_email_key'
    ) THEN
        ALTER TABLE user_directory ADD CONSTRAINT user_directory_email_key UNIQUE (email);
    END IF;
END $$;
