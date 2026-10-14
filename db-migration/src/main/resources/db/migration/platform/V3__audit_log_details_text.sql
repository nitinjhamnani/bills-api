-- Match the tenant-schema audit_log: plain descriptive text rather than structured JSONB.
ALTER TABLE audit_log ALTER COLUMN details TYPE TEXT USING details::text;
