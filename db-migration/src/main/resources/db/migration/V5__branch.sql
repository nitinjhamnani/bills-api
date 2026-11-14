CREATE TABLE branch (
    branch_id          varchar(8) PRIMARY KEY,
    branch_name        varchar(150) NOT NULL,
    branch_code        varchar(40),
    branch_address     varchar(500),
    branch_state       varchar(100),
    branch_location    varchar(150),
    branch_source_type varchar(16) NOT NULL,
    branch_source_id   varchar(64) NOT NULL,
    CONSTRAINT branch_id_check CHECK (branch_id ~ '^[A-Z0-9]{8}$'),
    CONSTRAINT branch_name_check CHECK (char_length(btrim(branch_name)) > 0)
);

CREATE INDEX branch_source_idx ON branch (branch_source_type, branch_source_id);
