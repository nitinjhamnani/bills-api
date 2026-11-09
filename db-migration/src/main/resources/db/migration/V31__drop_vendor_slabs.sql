DROP TABLE IF EXISTS slab_bid;
DROP TABLE IF EXISTS commission_slab;

ALTER TABLE bill_payment DROP COLUMN IF EXISTS assigned_vendor_client_id;
ALTER TABLE bill_payment DROP COLUMN IF EXISTS commission_percent;
ALTER TABLE bill_payment DROP COLUMN IF EXISTS commission_amount;
ALTER TABLE bill_payment DROP COLUMN IF EXISTS assigned_at;
