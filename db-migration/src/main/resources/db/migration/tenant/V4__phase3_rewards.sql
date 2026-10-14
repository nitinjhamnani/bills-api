-- Phase 3: commission slabs (per agency, per bill service, versioned by effective date) and
-- reward redemption requests. See implementation plan section 7.

CREATE TABLE IF NOT EXISTS commission_slab (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id           UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    bill_service_code   VARCHAR(30) NOT NULL,
    rate_type           VARCHAR(10) NOT NULL CHECK (rate_type IN ('FLAT', 'PERCENTAGE')),
    value               NUMERIC(10,4) NOT NULL,
    effective_from      DATE NOT NULL,
    effective_to        DATE, -- NULL = open-ended (current)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_commission_slab_lookup ON commission_slab (agency_id, bill_service_code, effective_from);

CREATE TABLE IF NOT EXISTS reward_redemption_request (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id                   UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    amount                      NUMERIC(14,2) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED/APPROVED/REJECTED/PROCESSING/PAID/FAILED
    beneficiary_account_number  VARCHAR(40),
    beneficiary_ifsc            VARCHAR(20),
    beneficiary_name            VARCHAR(150),
    provider_reference_id       VARCHAR(100),
    failure_reason              VARCHAR(500),
    idempotency_key             VARCHAR(150) UNIQUE NOT NULL,
    requested_by                VARCHAR(100),
    approved_by                 VARCHAR(100),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
