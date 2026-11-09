-- Vendor replaces Partner. New clients stay PENDING until Paytrix activates them.
ALTER TABLE client DROP CONSTRAINT client_client_type_check;
UPDATE client SET client_type = 'VENDOR' WHERE client_type = 'PARTNER';
ALTER TABLE client ADD CONSTRAINT client_client_type_check CHECK (client_type IN ('CORPORATE', 'VENDOR'));

CREATE TABLE client_onboarding (
    client_id uuid PRIMARY KEY REFERENCES client (id),
    legal_name varchar(200),
    registration_number varchar(100),
    registered_address varchar(500),
    details_submitted_at timestamptz,
    kyc_status varchar(20) NOT NULL DEFAULT 'NOT_STARTED',
    contract_status varchar(20) NOT NULL DEFAULT 'NOT_STARTED',
    CONSTRAINT client_onboarding_kyc_status_check CHECK (kyc_status IN ('NOT_STARTED', 'PENDING', 'COMPLETE')),
    CONSTRAINT client_onboarding_contract_status_check CHECK (contract_status IN ('NOT_STARTED', 'PENDING', 'SIGNED'))
);

CREATE TABLE commission_slab (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id varchar(50) NOT NULL,
    category_name varchar(150) NOT NULL,
    provider_biller_id varchar(50) NOT NULL,
    provider_name varchar(200) NOT NULL,
    min_amount numeric(14, 2) NOT NULL,
    max_amount numeric(14, 2),
    accepted_vendor_client_id uuid REFERENCES client (id),
    accepted_commission_percent numeric(6, 3),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commission_slab_min_nonneg CHECK (min_amount >= 0),
    CONSTRAINT commission_slab_amount_range_check CHECK (max_amount IS NULL OR max_amount >= min_amount)
);

CREATE TABLE slab_bid (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slab_id uuid NOT NULL REFERENCES commission_slab (id),
    vendor_client_id uuid NOT NULL REFERENCES client (id),
    commission_percent numeric(6, 3) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT slab_bid_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT slab_bid_percent_check CHECK (commission_percent >= 0 AND commission_percent <= 100)
);

CREATE UNIQUE INDEX idx_slab_bid_one_pending ON slab_bid (slab_id, vendor_client_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX idx_slab_bid_one_accepted ON slab_bid (slab_id) WHERE status = 'ACCEPTED';

ALTER TABLE bill_payment ADD COLUMN assigned_vendor_client_id uuid;
ALTER TABLE bill_payment ADD COLUMN commission_percent numeric(6, 3);
ALTER TABLE bill_payment ADD COLUMN commission_amount numeric(14, 2);
ALTER TABLE bill_payment ADD COLUMN assigned_at timestamptz;
