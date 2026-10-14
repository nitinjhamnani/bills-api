-- Phase 4: fee engine and bill disputes. (audit_log and role/permission tables already exist
-- from V1.) ledger_entry.reference_type has no DB-level CHECK constraint - it's a plain VARCHAR
-- constrained only by Hibernate's @Enumerated(STRING) - so adding the new FEE reference type
-- needs no schema change here.

-- audit_log.details was JSONB; the JPA entity mapping it (Phase 4) writes plain descriptive
-- text rather than structured JSON for simplicity, so switch the column to TEXT.
ALTER TABLE audit_log ALTER COLUMN details TYPE TEXT USING details::text;

CREATE TABLE IF NOT EXISTS fee_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_service_code   VARCHAR(30), -- NULL = applies to every service
    fee_type            VARCHAR(10) NOT NULL CHECK (fee_type IN ('FLAT', 'PERCENTAGE')),
    value               NUMERIC(10,4) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bill_dispute (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id             UUID NOT NULL REFERENCES bill(id) ON DELETE CASCADE,
    raised_by           VARCHAR(100),
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'RAISED', -- RAISED/UNDER_REVIEW/RESOLVED_VALID/RESOLVED_INVALID
    resolution_notes    VARCHAR(500),
    resolved_by         VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
