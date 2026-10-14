-- Phase 1: agencies, bill-service enablement + limits (company and agency level),
-- configurable maker-checker thresholds, and generic document storage metadata.

CREATE TABLE IF NOT EXISTS agency (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_code     VARCHAR(30) NOT NULL UNIQUE,
    agency_name     VARCHAR(200) NOT NULL,
    contact_phone   VARCHAR(15),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / SUSPENDED / TERMINATED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS agency_id UUID REFERENCES agency(id);

-- Which catalog bill services (platform.biller_service_catalog) this company has switched on
-- for its agencies, and the company-wide ceiling for that service.
CREATE TABLE IF NOT EXISTS company_bill_service_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_service_code   VARCHAR(30) NOT NULL UNIQUE,
    bill_service_name   VARCHAR(100) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    max_limit_amount    NUMERIC(14,2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-agency ceiling for a given bill service - must be <= the company's ceiling for that
-- service, and the service must be enabled at company level (enforced in the service layer).
CREATE TABLE IF NOT EXISTS agency_bill_service_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_id           UUID NOT NULL REFERENCES agency(id) ON DELETE CASCADE,
    bill_service_code   VARCHAR(30) NOT NULL,
    max_limit_amount    NUMERIC(14,2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (agency_id, bill_service_code)
);

-- Configurable maker-checker thresholds per money-moving action type. One row per action type;
-- amounts >= min_amount require `required_approver_count` distinct CHECKER approvals.
CREATE TABLE IF NOT EXISTS approval_rule (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type             VARCHAR(40) NOT NULL UNIQUE, -- BILL_PAYMENT / REWARD_PAYOUT / OFFLINE_TOPUP_CREDIT
    min_amount              NUMERIC(14,2) NOT NULL DEFAULT 0,
    required_approver_count INT NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO approval_rule (action_type, min_amount, required_approver_count) VALUES
    ('BILL_PAYMENT', 0, 1),
    ('REWARD_PAYOUT', 0, 1),
    ('OFFLINE_TOPUP_CREDIT', 0, 1)
ON CONFLICT (action_type) DO NOTHING;

-- Generic document metadata (KYC, agency agreements, offline deposit receipts, ...). The file
-- itself is stored on local disk under app.documents.storage-dir for Phase 1-4; swap for
-- GCS/S3 later without changing this table.
CREATE TABLE IF NOT EXISTS document (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type      VARCHAR(40) NOT NULL, -- AGENCY_KYC / OFFLINE_TOPUP_RECEIPT / ...
    owner_id        VARCHAR(100),
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100),
    storage_path    VARCHAR(500) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW', -- PENDING_REVIEW / APPROVED / REJECTED
    uploaded_by     VARCHAR(100),
    reviewed_by     VARCHAR(100),
    review_notes    VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
