CREATE TABLE vendor_reimbursement (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_client_id uuid NOT NULL REFERENCES client (id),
    total_amount numeric(14, 2) NOT NULL,
    reference varchar(120) NOT NULL,
    paid_at timestamptz NOT NULL,
    actor varchar(50) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT vendor_reimbursement_reference_check CHECK (char_length(btrim(reference)) > 0)
);

CREATE TABLE commission_cycle (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    vendor_client_id uuid NOT NULL REFERENCES client (id),
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_amount numeric(14, 2) NOT NULL,
    total_commission numeric(14, 2) NOT NULL,
    status varchar(10) NOT NULL,
    receipt_reference varchar(120),
    closed_at timestamptz,
    actor varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commission_cycle_status_check CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT commission_cycle_period_check CHECK (period_end >= period_start)
);

ALTER TABLE bill_payment
    ADD COLUMN vendor_stage varchar(20),
    ADD COLUMN bbps_verified_at timestamptz,
    ADD COLUMN bbps_check_note varchar(500),
    ADD COLUMN reimbursed_at timestamptz,
    ADD COLUMN reimbursement_id uuid REFERENCES vendor_reimbursement (id),
    ADD COLUMN commission_cycle_id uuid REFERENCES commission_cycle (id),
    ADD CONSTRAINT bill_payment_vendor_stage_check CHECK (
        vendor_stage IS NULL OR vendor_stage IN ('ASSIGNED', 'IN_PROGRESS', 'VENDOR_PAID', 'VERIFIED', 'REIMBURSED')
    );

UPDATE bill_payment
SET vendor_stage = CASE
    WHEN org_status = 'PAID' THEN 'REIMBURSED'
    WHEN vendor_marked_paid_at IS NOT NULL THEN 'VENDOR_PAID'
    ELSE 'ASSIGNED'
END
WHERE assigned_vendor_client_id IS NOT NULL
  AND vendor_stage IS NULL;

CREATE INDEX bill_payment_vendor_stage_idx ON bill_payment (vendor_stage) WHERE vendor_stage IS NOT NULL;
CREATE INDEX bill_payment_reimbursed_at_idx ON bill_payment (assigned_vendor_client_id, reimbursed_at);
