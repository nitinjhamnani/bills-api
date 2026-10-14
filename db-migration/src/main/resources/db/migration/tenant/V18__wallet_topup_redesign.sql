-- Wallet top-up collapses to one request type: online (instant, no approval) and offline
-- (receipt, needs approval) are replaced by a single always-needs-approval request carrying a
-- payment mode (RTGS/NEFT/UPI/CASH/OTHER), a free-text payment reference, and an optional proof.
ALTER TABLE wallet_topup_request DROP CONSTRAINT IF EXISTS wallet_topup_request_method_check;
ALTER TABLE wallet_topup_request RENAME COLUMN method TO payment_mode;
ALTER TABLE wallet_topup_request ALTER COLUMN payment_mode TYPE VARCHAR(10);
-- Old ONLINE/OFFLINE values don't map to a real payment rail - existing rows just become OTHER.
UPDATE wallet_topup_request SET payment_mode = 'OTHER' WHERE payment_mode IN ('ONLINE', 'OFFLINE');
ALTER TABLE wallet_topup_request ADD CONSTRAINT wallet_topup_request_payment_mode_check
    CHECK (payment_mode IN ('RTGS', 'NEFT', 'UPI', 'CASH', 'OTHER'));

ALTER TABLE wallet_topup_request ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(150);

ALTER TABLE wallet_topup_request DROP COLUMN IF EXISTS provider_reference_id;
