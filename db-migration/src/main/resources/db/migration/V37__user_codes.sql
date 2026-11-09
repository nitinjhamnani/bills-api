ALTER TABLE app_user ADD COLUMN user_code varchar(6);
ALTER TABLE user_directory ADD COLUMN user_code varchar(6);

DO $$
DECLARE
    user_row record;
    candidate varchar(6);
    alphabet constant text := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    i int;
BEGIN
    FOR user_row IN SELECT id FROM app_user WHERE user_code IS NULL LOOP
        LOOP
            candidate := '';
            FOR i IN 1..6 LOOP
                candidate := candidate || substr(alphabet, 1 + floor(random() * 36)::int, 1);
            END LOOP;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM app_user WHERE user_code = candidate);
        END LOOP;
        UPDATE app_user SET user_code = candidate WHERE id = user_row.id;
    END LOOP;
END $$;

UPDATE user_directory AS directory
SET user_code = app_user.user_code
FROM app_user
WHERE directory.user_id = app_user.id
  AND directory.user_code IS NULL;

DO $$
DECLARE
    directory_row record;
    candidate varchar(6);
    alphabet constant text := 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    i int;
BEGIN
    FOR directory_row IN SELECT id FROM user_directory WHERE user_code IS NULL LOOP
        LOOP
            candidate := '';
            FOR i IN 1..6 LOOP
                candidate := candidate || substr(alphabet, 1 + floor(random() * 36)::int, 1);
            END LOOP;
            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM user_directory WHERE user_code = candidate
                UNION ALL
                SELECT 1 FROM app_user WHERE user_code = candidate
            );
        END LOOP;
        UPDATE user_directory SET user_code = candidate WHERE id = directory_row.id;
    END LOOP;
END $$;

ALTER TABLE app_user ALTER COLUMN user_code SET NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT app_user_user_code_key UNIQUE (user_code);

ALTER TABLE user_directory ALTER COLUMN user_code SET NOT NULL;
ALTER TABLE user_directory ADD CONSTRAINT user_directory_user_code_key UNIQUE (user_code);

UPDATE bill_payment_log AS log
SET description = replace(log.description, app_user.phone_number, app_user.user_code),
    actor = app_user.user_code
FROM app_user
WHERE log.actor = app_user.phone_number;

UPDATE audit_log AS log
SET details = replace(log.details, app_user.phone_number, app_user.user_code),
    actor_user_id = app_user.user_code
FROM app_user
WHERE log.actor_user_id = app_user.phone_number;

UPDATE commission_rule_history AS history
SET actor = app_user.user_code
FROM app_user
WHERE history.actor = app_user.phone_number;

UPDATE commission_rate_proposal AS proposal
SET decided_by = app_user.user_code
FROM app_user
WHERE proposal.decided_by = app_user.phone_number;
