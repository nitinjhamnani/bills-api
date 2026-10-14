-- Agency-role users (AGENCY_ADMIN / AGENCY_OPERATOR) need their agency id embedded in the login
-- response/JWT so the API can scope "your own agency's wallet/bills" without an extra DB lookup
-- on every request.
ALTER TABLE user_directory ADD COLUMN IF NOT EXISTS agency_id UUID;
