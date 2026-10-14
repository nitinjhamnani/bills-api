-- Persists the BBPS fetch response's metadata and amount breakdown permanently (previously shown
-- only transiently during bill creation), so the bill details page can display exactly what the
-- biller returned. Nullable since bulk-upload/auto-pay bills never go through a fetch call.
ALTER TABLE bill ADD COLUMN IF NOT EXISTS bill_number VARCHAR(50);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS bill_date VARCHAR(20);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS due_date VARCHAR(20);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS bill_period VARCHAR(20);
ALTER TABLE bill ADD COLUMN IF NOT EXISTS amount_breakdown TEXT;
