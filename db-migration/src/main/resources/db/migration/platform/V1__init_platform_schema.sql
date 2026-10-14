-- Platform schema: cross-tenant registry + shared catalog. Runs once at app startup via
-- Spring Boot's autoconfigured Flyway (see application.yml: spring.flyway.schemas=platform).

CREATE TABLE IF NOT EXISTS user_directory (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15) NOT NULL UNIQUE,
    user_id         UUID NOT NULL,
    tenant_schema   VARCHAR(63),            -- NULL for platform admins
    role            VARCHAR(50) NOT NULL,
    display_name    VARCHAR(150),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS company (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_code    VARCHAR(30) NOT NULL UNIQUE,
    company_name    VARCHAR(200) NOT NULL,
    tenant_schema   VARCHAR(63) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- PENDING/ACTIVE/SUSPENDED (full KYC workflow: Phase 1)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS biller_service_catalog (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_code    VARCHAR(30) NOT NULL UNIQUE,
    service_name    VARCHAR(100) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   VARCHAR(100),
    actor_role      VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100),
    entity_id       VARCHAR(100),
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed: a default platform admin so there's someone who can log in and provision the first
-- company. Local/dev only - phone number and role are fixed via this migration; rotate/replace
-- for anything beyond local development.
INSERT INTO user_directory (phone_number, user_id, tenant_schema, role, display_name, status)
VALUES ('9999999999', gen_random_uuid(), NULL, 'PLATFORM_ADMIN', 'Platform Admin', 'ACTIVE')
ON CONFLICT (phone_number) DO NOTHING;

-- Seed: a starter bill-service catalog. Companies enable a subset of these for their agencies
-- (see tenant schema's company_bill_service_config, added in Phase 1).
INSERT INTO biller_service_catalog (service_code, service_name, category) VALUES
    ('ELECTRICITY', 'Electricity Bill', 'UTILITY'),
    ('WATER', 'Water Bill', 'UTILITY'),
    ('GAS', 'Piped Gas Bill', 'UTILITY'),
    ('DTH', 'DTH / Cable TV', 'ENTERTAINMENT'),
    ('BROADBAND', 'Broadband / Fibernet', 'TELECOM'),
    ('MOBILE_POSTPAID', 'Mobile Postpaid', 'TELECOM'),
    ('LANDLINE', 'Landline', 'TELECOM'),
    ('INSURANCE_PREMIUM', 'Insurance Premium', 'INSURANCE')
ON CONFLICT (service_code) DO NOTHING;
