-- A rule is the coverage. Each selected vendor has their own rate.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM commission_rule rule
        WHERE (
            SELECT count(*) FROM commission_rule_range link WHERE link.rule_id = rule.id
        ) <> 1
    ) THEN
        RAISE EXCEPTION 'each commission rule must cover exactly one amount range';
    END IF;
END $$;

CREATE TABLE commission_rule_vendor (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id uuid NOT NULL REFERENCES commission_rule (id) ON DELETE CASCADE,
    vendor_client_id uuid NOT NULL REFERENCES client (id),
    commission_percent numeric(6, 3),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commission_rule_vendor_percent_check CHECK (
        commission_percent IS NULL OR (commission_percent > 0 AND commission_percent <= 100)
    ),
    CONSTRAINT commission_rule_vendor_unique UNIQUE (rule_id, vendor_client_id)
);

CREATE UNIQUE INDEX commission_rule_vendor_percent_unique
    ON commission_rule_vendor (rule_id, commission_percent)
    WHERE commission_percent IS NOT NULL;

CREATE INDEX idx_commission_rule_vendor_vendor ON commission_rule_vendor (vendor_client_id);

INSERT INTO commission_rule_vendor (rule_id, vendor_client_id, commission_percent, active, created_at, updated_at)
SELECT id, vendor_client_id, commission_percent, true, created_at, updated_at
FROM commission_rule;

ALTER TABLE commission_rule ADD COLUMN range_id uuid REFERENCES commission_amount_range (id);

UPDATE commission_rule rule
SET range_id = (
    SELECT link.range_id FROM commission_rule_range link WHERE link.rule_id = rule.id
);

ALTER TABLE commission_rule ALTER COLUMN range_id SET NOT NULL;

CREATE UNIQUE INDEX commission_rule_one_range ON commission_rule (range_id);

DROP TABLE commission_rule_range;

ALTER TABLE commission_rule DROP COLUMN vendor_client_id;
ALTER TABLE commission_rule DROP COLUMN commission_percent;

ALTER TABLE commission_rate_proposal ADD COLUMN vendor_client_id uuid REFERENCES client (id);

UPDATE commission_rate_proposal proposal
SET vendor_client_id = (
    SELECT vendor.vendor_client_id
    FROM commission_rule_vendor vendor
    WHERE vendor.rule_id = proposal.rule_id
    LIMIT 1
);

ALTER TABLE commission_rate_proposal ALTER COLUMN vendor_client_id SET NOT NULL;

DROP INDEX idx_commission_rate_one_pending;

CREATE UNIQUE INDEX idx_commission_rate_one_pending
    ON commission_rate_proposal (rule_id, vendor_client_id)
    WHERE status = 'PENDING';

CREATE TABLE commission_rule_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id uuid NOT NULL REFERENCES commission_rule (id) ON DELETE CASCADE,
    vendor_client_id uuid REFERENCES client (id),
    event_type varchar(40) NOT NULL,
    commission_percent numeric(6, 3),
    actor varchar(50) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT commission_rule_history_event_check CHECK (event_type IN (
        'VENDOR_ADDED', 'VENDOR_REMOVED', 'RATE_ENTERED', 'BID_SUBMITTED',
        'BID_ACCEPTED', 'BID_REJECTED', 'VENDOR_PAUSED', 'VENDOR_RESUMED',
        'RULE_ACTIVATED', 'RULE_DEACTIVATED'
    ))
);

CREATE INDEX idx_commission_rule_history_rule ON commission_rule_history (rule_id, created_at);

INSERT INTO commission_rule_history (rule_id, vendor_client_id, event_type, commission_percent, actor, created_at)
SELECT rule_id, vendor_client_id, 'RATE_ENTERED', commission_percent, 'Paytrix', created_at
FROM commission_rule_vendor
WHERE commission_percent IS NOT NULL;
