ALTER TABLE bill DROP COLUMN IF EXISTS provider_location_id;

DROP INDEX IF EXISTS idx_commission_rule_match;

ALTER TABLE commission_rule DROP COLUMN IF EXISTS location_id;
ALTER TABLE commission_rule DROP COLUMN IF EXISTS location_name;

CREATE INDEX idx_commission_rule_match
    ON commission_rule (category_id, provider_biller_id);

DROP TABLE IF EXISTS organisation_location_config;
DROP TABLE IF EXISTS bbps_location;
