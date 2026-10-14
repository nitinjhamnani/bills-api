-- Restore the bill-settlement approval rule. Maker-checker uses existing
-- org_status IN_PROCESS as the wait; no new payment status is added.
INSERT INTO approval_rule (action_type, min_amount, required_approver_count)
VALUES ('BILL_PAYMENT', 0, 1)
ON CONFLICT (action_type) DO NOTHING;
