CREATE TABLE commission_log (
    id             uuid PRIMARY KEY,
    commission_id  uuid NOT NULL,
    action         varchar(32) NOT NULL,
    summary        varchar(500) NOT NULL,
    actor_user_id  varchar(6),
    actor_name     varchar(150),
    created_at     timestamptz NOT NULL
);

CREATE INDEX commission_log_commission_idx ON commission_log (commission_id, created_at DESC);
