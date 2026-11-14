CREATE TABLE range (
    range_id  uuid PRIMARY KEY,
    min       numeric(14, 2) NOT NULL,
    max       numeric(14, 2),
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT range_min_check CHECK (min >= 0),
    CONSTRAINT range_bounds_check CHECK (max IS NULL OR max >= min)
);
