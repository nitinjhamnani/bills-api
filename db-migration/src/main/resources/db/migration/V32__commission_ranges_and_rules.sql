CREATE TABLE commission_amount_range (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    min_amount numeric(14, 2) NOT NULL,
    max_amount numeric(14, 2),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commission_amount_range_min_nonneg CHECK (min_amount >= 0),
    CONSTRAINT commission_amount_range_bounds CHECK (max_amount IS NULL OR max_amount >= min_amount)
);

CREATE TABLE commission_rule (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_client_id uuid NOT NULL REFERENCES client (id),
    category_id varchar(64) NOT NULL,
    category_name varchar(150) NOT NULL,
    location_id varchar(64) NOT NULL,
    location_name varchar(150) NOT NULL,
    provider_biller_id varchar(64) NOT NULL,
    provider_name varchar(200) NOT NULL,
    commission_percent numeric(6, 3) NOT NULL,
    priority integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commission_rule_percent_check CHECK (commission_percent > 0 AND commission_percent <= 100),
    CONSTRAINT commission_rule_priority_check CHECK (priority >= 1)
);

CREATE INDEX idx_commission_rule_match
    ON commission_rule (category_id, location_id, provider_biller_id);

CREATE TABLE commission_rule_range (
    rule_id uuid NOT NULL REFERENCES commission_rule (id) ON DELETE CASCADE,
    range_id uuid NOT NULL REFERENCES commission_amount_range (id),
    PRIMARY KEY (rule_id, range_id)
);

CREATE TABLE commission_rate_proposal (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id uuid NOT NULL REFERENCES commission_rule (id) ON DELETE CASCADE,
    proposed_percent numeric(6, 3) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    decided_at timestamptz,
    decided_by varchar(20),
    CONSTRAINT commission_rate_proposal_percent_check CHECK (proposed_percent > 0 AND proposed_percent <= 100),
    CONSTRAINT commission_rate_proposal_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED'))
);

CREATE UNIQUE INDEX idx_commission_rate_one_pending
    ON commission_rate_proposal (rule_id) WHERE status = 'PENDING';

ALTER TABLE bill_payment ADD COLUMN assigned_vendor_client_id uuid;
ALTER TABLE bill_payment ADD COLUMN commission_rule_id uuid;
ALTER TABLE bill_payment ADD COLUMN commission_percent numeric(6, 3);
ALTER TABLE bill_payment ADD COLUMN commission_amount numeric(14, 2);
ALTER TABLE bill_payment ADD COLUMN assigned_at timestamptz;
ALTER TABLE bill_payment ADD COLUMN vendor_marked_paid_at timestamptz;
ALTER TABLE bill_payment ADD COLUMN vendor_payment_reference varchar(120);

CREATE INDEX idx_bill_payment_assigned_vendor ON bill_payment (assigned_vendor_client_id);
