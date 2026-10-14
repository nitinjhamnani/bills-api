-- The company-wide bill-service ceiling was never enforced anywhere after the agency-level limit
-- (and its fallback amount check) was removed in V14 - only enable/disable is a real control now.
ALTER TABLE company_bill_service_config DROP COLUMN IF EXISTS max_limit_amount;
