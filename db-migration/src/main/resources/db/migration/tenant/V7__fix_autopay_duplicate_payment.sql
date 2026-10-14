-- Bug fix: AutoFetchJob refreshes lastFetchedAmountDue on every tick regardless of whether it
-- was already paid, and the (dummy, stateless) BillingProvider always reports the same amount
-- due for an unpaid-looking account. Without tracking which due date/cycle was last actually
-- paid, AutoPayJob would pay the same fetched due again on the very next cycle, silently
-- draining the wallet. last_paid_due_date lets AutoPayJob compare "what's due now" against
-- "what I already paid" and skip if they match.
ALTER TABLE customer_bill_account ADD COLUMN IF NOT EXISTS last_paid_due_date VARCHAR(20);
