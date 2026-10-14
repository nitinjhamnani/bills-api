-- The "Save account" flow now picks a specific biller (like Create Bill) and captures the
-- customer's mobile number, instead of only a bare category + account number.
ALTER TABLE customer_bill_account ADD COLUMN IF NOT EXISTS biller_id VARCHAR(50);
ALTER TABLE customer_bill_account ADD COLUMN IF NOT EXISTS biller_name VARCHAR(150);
ALTER TABLE customer_bill_account ADD COLUMN IF NOT EXISTS customer_mobile_number VARCHAR(15);
