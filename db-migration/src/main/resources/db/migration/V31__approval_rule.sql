CREATE TABLE approval_rule (
    id                      uuid PRIMARY KEY,
    action_type             varchar(255) NOT NULL,
    min_amount              numeric(14, 2) NOT NULL,
    required_approver_count integer NOT NULL,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT approval_rule_action_key UNIQUE (action_type),
    CONSTRAINT approval_rule_min_amount_check CHECK (min_amount >= 0),
    CONSTRAINT approval_rule_approvers_check CHECK (required_approver_count >= 0)
);
