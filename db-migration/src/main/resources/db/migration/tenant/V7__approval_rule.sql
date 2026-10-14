CREATE TABLE approval_rule (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type             varchar(40) NOT NULL,
    min_amount              numeric(14,2) NOT NULL DEFAULT 0,
    required_approver_count integer NOT NULL DEFAULT 1,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT approval_rule_action_type_key UNIQUE (action_type)
);

INSERT INTO approval_rule (action_type, min_amount, required_approver_count) VALUES
    ('BILL_PAYMENT', 0, 1),
    ('OFFLINE_TOPUP_CREDIT', 0, 1)
ON CONFLICT (action_type) DO NOTHING;
