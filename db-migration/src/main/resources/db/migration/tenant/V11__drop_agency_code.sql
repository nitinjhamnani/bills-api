-- Agencies are identified by their UUID id everywhere (URLs, foreign keys, lookups) - agency_code
-- was never used as a real key, only as a cosmetic label the onboarding flow auto-generated from
-- the agency name. Dropping it removes that dead uniqueness constraint and generation logic.
ALTER TABLE agency DROP COLUMN IF EXISTS agency_code;
