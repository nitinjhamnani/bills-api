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

-- Commissions that already exist get one starting line so their details page is not blank.
INSERT INTO commission_log (id, commission_id, action, summary, actor_user_id, actor_name, created_at)
SELECT gen_random_uuid(),
       c.id,
       'CREATED',
       'Commission was already on file when history started.',
       c.created_by_id,
       u.name,
       c.created_at
FROM commissions c
LEFT JOIN app_user u ON u.user_id = c.created_by_id;
