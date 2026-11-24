-- An account number can exist once. Where the same number is already on COZSR745 and
-- another company, the COZSR745 bill is removed so this index can be created.
DO $$
DECLARE
    removed text;
    leftover text;
BEGIN
    CREATE TEMP TABLE duplicate_cozsr_bills ON COMMIT DROP AS
    SELECT b.bill_id
    FROM bills b
    WHERE b.source_id = 'COZSR745'
      AND EXISTS (
          SELECT 1
          FROM bills other
          WHERE other.source_id <> 'COZSR745'
            AND lower(btrim(other.primary_param_value)) = lower(btrim(b.primary_param_value))
      );

    SELECT string_agg(bill_id, ', ' ORDER BY bill_id) INTO removed FROM duplicate_cozsr_bills;
    IF removed IS NULL THEN
        RAISE NOTICE 'No COZSR745 bill duplicates another company';
    ELSE
        RAISE NOTICE 'Removing COZSR745 bills that duplicate another company: %', removed;

        UPDATE bills
        SET latest_bill_fetch_id = NULL
        WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills);

        DELETE FROM bill_payment_rejection
        WHERE bill_payment_id IN (
            SELECT id FROM bill_payment
            WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills)
        );

        DELETE FROM bill_dispute
        WHERE bill_payment_id IN (
            SELECT id FROM bill_payment
            WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills)
        );

        DELETE FROM vendor_payments
        WHERE bill_payment_id IN (
            SELECT id FROM bill_payment
            WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills)
        );

        DELETE FROM bill_payment_log
        WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills)
           OR bill_payment_id IN (
                SELECT id FROM bill_payment
                WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills)
           );

        DELETE FROM bill_payment
        WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills);

        DELETE FROM bill_fetch
        WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills);

        DELETE FROM bills
        WHERE bill_id IN (SELECT bill_id FROM duplicate_cozsr_bills);
    END IF;

    SELECT string_agg(account, ', ' ORDER BY account) INTO leftover
    FROM (
        SELECT lower(btrim(primary_param_value)) AS account
        FROM bills
        GROUP BY 1
        HAVING count(*) > 1
    ) duplicates;

    IF leftover IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate account numbers remain: %', leftover;
    END IF;
END $$;

DROP INDEX IF EXISTS bills_corp_primary_param_uidx;

CREATE UNIQUE INDEX IF NOT EXISTS bills_primary_param_uidx
    ON bills (lower(btrim(primary_param_value)));
