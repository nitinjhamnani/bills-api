-- Bill submission becomes a two-step flow: an agency first creates a draft bill (wallet untouched,
-- payment status CREATED / bill status UNPAID), then separately passes it to the company, which is
-- when the wallet is actually debited and the bill enters the existing QUEUED settlement pipeline.
-- No existing rows use the old defaults ('PENDING' wallet status was never actually persisted -
-- submission always wrote 'PAID' directly), so this only changes the default for new rows.
ALTER TABLE bill ALTER COLUMN wallet_payment_status SET DEFAULT 'CREATED';
ALTER TABLE bill ALTER COLUMN bill_settlement_status SET DEFAULT 'UNPAID';
