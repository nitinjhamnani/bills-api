-- Commission slabs are removed - reward accrual is now a manual, TBD workflow instead of an
-- automatic per-agency-per-service rate. Agency bill-service config drops its amount ceiling too;
-- only the company-wide ceiling and the agency's own wallet balance are checked at bill time now.
DROP TABLE IF EXISTS commission_slab;
ALTER TABLE agency_bill_service_config DROP COLUMN IF EXISTS max_limit_amount;
