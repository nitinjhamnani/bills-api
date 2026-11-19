-- Persist client lifecycle status (including TERMINATED), onboarding metadata, and created_at timestamps.
ALTER TABLE corp_details
    ADD COLUMN IF NOT EXISTS account_status varchar(20),
    ADD COLUMN IF NOT EXISTS registration_number varchar(100),
    ADD COLUMN IF NOT EXISTS signing_authority varchar(200),
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

UPDATE corp_details
SET account_status = CASE
    WHEN is_active THEN 'ACTIVE'
    WHEN onboarding_status = 'COMPLETE' AND agreement_status = 'SIGNED' THEN 'SUSPENDED'
    ELSE 'PENDING'
END
WHERE account_status IS NULL;

ALTER TABLE corp_details
    ALTER COLUMN account_status SET NOT NULL,
    ALTER COLUMN account_status SET DEFAULT 'PENDING';

ALTER TABLE vendor_details
    ADD COLUMN IF NOT EXISTS onboarding_status varchar(20),
    ADD COLUMN IF NOT EXISTS account_status varchar(20),
    ADD COLUMN IF NOT EXISTS registration_number varchar(100),
    ADD COLUMN IF NOT EXISTS signing_authority varchar(200),
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

UPDATE vendor_details
SET onboarding_status = CASE
    WHEN agreement_status = 'SIGNED' THEN 'COMPLETE'
    WHEN agreement_status = 'PENDING' THEN 'COMPLETE'
    ELSE 'NOT_STARTED'
END
WHERE onboarding_status IS NULL;

UPDATE vendor_details
SET account_status = CASE
    WHEN is_active THEN 'ACTIVE'
    WHEN agreement_status = 'SIGNED' THEN 'SUSPENDED'
    ELSE 'PENDING'
END
WHERE account_status IS NULL;

ALTER TABLE vendor_details
    ALTER COLUMN onboarding_status SET NOT NULL,
    ALTER COLUMN onboarding_status SET DEFAULT 'NOT_STARTED',
    ALTER COLUMN account_status SET NOT NULL,
    ALTER COLUMN account_status SET DEFAULT 'PENDING';

-- Persist per-bill automation toggles and provider operator ID.
ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS operator_id varchar(80),
    ADD COLUMN IF NOT EXISTS auto_fetch_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS auto_pay_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();

-- Persist optional human-readable notes/descriptions on wallet and platform ledger rows.
ALTER TABLE wallet_transactions
    ADD COLUMN IF NOT EXISTS description varchar(300);

ALTER TABLE platform_transaction
    ADD COLUMN IF NOT EXISTS description varchar(300);

-- Prevent truncation errors on audit logs and dispute notes.
ALTER TABLE audit_log
    ALTER COLUMN details TYPE text;

ALTER TABLE bill_dispute
    ALTER COLUMN reason TYPE varchar(1000),
    ALTER COLUMN resolution_notes TYPE varchar(1000);
