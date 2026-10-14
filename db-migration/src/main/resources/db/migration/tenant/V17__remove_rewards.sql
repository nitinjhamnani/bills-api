-- Rewards is removed completely: nothing ever accrued into the reward wallet after commission
-- slabs were dropped (V14), and the redemption/payout flow doesn't fit the product going
-- forward. Plain wallet (WALLET type) is untouched.
DELETE FROM ledger_entry WHERE wallet_account_id IN (SELECT id FROM wallet_account WHERE wallet_type = 'REWARD');
DELETE FROM wallet_account WHERE wallet_type = 'REWARD';

ALTER TABLE wallet_account DROP CONSTRAINT IF EXISTS wallet_account_wallet_type_check;
ALTER TABLE wallet_account ADD CONSTRAINT wallet_account_wallet_type_check CHECK (wallet_type = 'WALLET');

DROP TABLE IF EXISTS reward_redemption_request;

DELETE FROM approval_rule WHERE action_type = 'REWARD_PAYOUT';
