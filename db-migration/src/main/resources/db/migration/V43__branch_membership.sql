-- A person can sign in to more than one branch, and a branch can have more than one person.
CREATE TABLE branch_user (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    branch_id   UUID NOT NULL REFERENCES branch (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT branch_user_pair_unique UNIQUE (user_id, branch_id)
);

CREATE INDEX branch_user_branch_idx ON branch_user (branch_id);

INSERT INTO branch_user (user_id, branch_id)
SELECT id, branch_id
FROM app_user
WHERE branch_id IS NOT NULL;
