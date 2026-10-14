-- Lets a company admin switch a bill service on/off for one agency without losing its configured
-- limit (implementation plan &sect;4.3 "service activation/deactivation" - the company-level
-- catalog config already has this same enabled flag, see company_bill_service_config).
ALTER TABLE agency_bill_service_config ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
