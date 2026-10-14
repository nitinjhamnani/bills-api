-- The "Create bill" flow now asks for a specific biller/provider (see platform.biller) and the
-- customer's mobile number, not just the bill-service category. Nullable since bulk-upload and
-- auto-pay bills are still submitted by category alone, with no specific biller on file.
ALTER TABLE bill ADD COLUMN IF NOT EXISTS customer_mobile_number VARCHAR(15);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS biller_id VARCHAR(50);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS biller_name VARCHAR(200);
