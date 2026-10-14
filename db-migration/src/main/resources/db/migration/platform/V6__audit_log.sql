CREATE TABLE platform.audit_log (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id   varchar(100),
    actor_role      varchar(50),
    action          varchar(100) NOT NULL,
    entity_type     varchar(100),
    entity_id       varchar(100),
    details         text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_platform_audit_log_created
    ON platform.audit_log (created_at DESC);
