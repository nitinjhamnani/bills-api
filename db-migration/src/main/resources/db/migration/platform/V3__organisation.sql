CREATE TABLE platform.organisation (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_code   varchar(30) NOT NULL,
    organisation_name   varchar(200) NOT NULL,
    tenant_schema       varchar(63) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT organisation_code_key UNIQUE (organisation_code),
    CONSTRAINT organisation_tenant_schema_key UNIQUE (tenant_schema)
);

CREATE INDEX idx_organisation_created
    ON platform.organisation (created_at DESC);
