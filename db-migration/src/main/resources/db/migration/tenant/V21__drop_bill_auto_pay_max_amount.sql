-- Auto-pay now pays whatever amount is due, with no cap - the max-amount field added
-- friction without a corresponding safety need (the organisation still manually settles
-- every bill with the biller, so there's no unattended money movement past the client's wallet).
ALTER TABLE bill DROP COLUMN IF EXISTS auto_pay_max_amount;
