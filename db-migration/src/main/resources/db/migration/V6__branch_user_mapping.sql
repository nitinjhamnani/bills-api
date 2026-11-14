CREATE TABLE branch_user_mapping (
    branch_id varchar(8) NOT NULL REFERENCES branch (branch_id),
    user_id   varchar(6) NOT NULL REFERENCES app_user (user_id),
    PRIMARY KEY (branch_id, user_id)
);

CREATE INDEX branch_user_mapping_user_idx ON branch_user_mapping (user_id);
