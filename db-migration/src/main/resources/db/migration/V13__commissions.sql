CREATE TABLE commissions (
    id            uuid PRIMARY KEY,
    min_range     numeric(14, 2) NOT NULL,
    max_range     numeric(14, 2),
    category_id   uuid NOT NULL REFERENCES bbps_category (id),
    operator_id   varchar(80) NOT NULL,
    operator_name varchar(300) NOT NULL,
    vendor_id     varchar(8) NOT NULL REFERENCES vendor_details (vendor_id),
    vendor_name   varchar(200) NOT NULL,
    commission    numeric(6, 3) NOT NULL,
    status        varchar(16) NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    created_by_id varchar(6),
    updated_by_id varchar(6),
    is_active     boolean NOT NULL DEFAULT true,
    CONSTRAINT commissions_min_range_check CHECK (min_range >= 0),
    CONSTRAINT commissions_range_check CHECK (max_range IS NULL OR max_range >= min_range),
    CONSTRAINT commissions_commission_check CHECK (commission > 0 AND commission <= 100)
);

CREATE INDEX commissions_match_idx ON commissions (category_id, operator_id) WHERE is_active;
CREATE INDEX commissions_vendor_idx ON commissions (vendor_id);
