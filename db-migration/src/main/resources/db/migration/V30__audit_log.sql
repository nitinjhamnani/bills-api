CREATE TABLE audit_log (
    id            uuid PRIMARY KEY,
    actor_user_id varchar(255),
    actor_role    varchar(255),
    action        varchar(255) NOT NULL,
    entity_type   varchar(255),
    entity_id     varchar(255),
    details       varchar(255),
    created_at    timestamptz NOT NULL
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_user_id, created_at DESC);
