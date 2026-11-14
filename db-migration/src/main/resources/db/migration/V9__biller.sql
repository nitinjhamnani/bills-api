CREATE TABLE biller (
    id          uuid PRIMARY KEY,
    biller_id   varchar(50) NOT NULL,
    biller_name varchar(200) NOT NULL,
    category    varchar(30) NOT NULL,
    state       varchar(50),
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT biller_biller_id_key UNIQUE (biller_id)
);

CREATE INDEX biller_category_idx ON biller (category) WHERE active;
