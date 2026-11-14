CREATE TABLE category_operators (
    id              uuid PRIMARY KEY,
    operator_name   varchar(300) NOT NULL,
    operator_id     varchar(80) NOT NULL,
    provider        varchar(32) NOT NULL,
    location        varchar(100),
    operator_params jsonb,
    category_id     uuid NOT NULL REFERENCES bbps_category (id),
    is_active       boolean NOT NULL DEFAULT true,
    CONSTRAINT category_operators_category_operator_key UNIQUE (category_id, operator_id)
);

CREATE INDEX category_operators_category_idx ON category_operators (category_id) WHERE is_active;
CREATE INDEX category_operators_operator_idx ON category_operators (operator_id) WHERE is_active;
