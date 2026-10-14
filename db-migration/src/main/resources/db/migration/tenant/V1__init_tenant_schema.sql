-- Tenant schema template: run programmatically against `tenant_<companyCode>` by
-- CompanyProvisioningService when a new company is onboarded (see api-app's
-- CompanyProvisioningService, platform-module). Every tenant schema gets an identical copy of
-- this structure, plus its own flyway_schema_history for independent versioning.

CREATE TABLE IF NOT EXISTS role (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS permission (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id         UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS app_user (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number    VARCHAR(15) NOT NULL UNIQUE,
    full_name       VARCHAR(150) NOT NULL,
    role_id         UUID NOT NULL REFERENCES role(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
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

-- Seed default roles for every company. Fine-grained permissions (role_permission rows) are
-- populated starting Phase 1 alongside the actual RBAC-guarded endpoints that need them.
INSERT INTO role (name, description) VALUES
    ('COMPANY_ADMIN', 'Full control over company, agencies, users, RBAC and reports'),
    ('MAKER', 'Can initiate bill payments and other money-moving actions'),
    ('CHECKER', 'Approves maker-initiated financial actions (maker-checker)'),
    ('VIEWER', 'Read-only access to company data'),
    ('AGENCY_ADMIN', 'Manages an agency: customers, wallet, bulk uploads, auto-pay config'),
    ('AGENCY_OPERATOR', 'Day-to-day agency operations under an Agency Admin')
ON CONFLICT (name) DO NOTHING;
