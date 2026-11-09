-- The next bill payment must send this fetch token back unchanged.
ALTER TABLE bill ADD COLUMN provider_fetch_token text;
